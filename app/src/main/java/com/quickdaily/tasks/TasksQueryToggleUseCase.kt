package com.quickdaily.tasks

import android.content.Context
import android.media.MediaActionSound
import com.quickdaily.BetaLogger
import com.quickdaily.TaskCompletionSoundPolicy
import com.quickdaily.TaskCompletionTimestampPolicy
import com.quickdaily.WidgetRefreshHelper
import com.quickdaily.util.ContentUtil
import com.quickdaily.util.DateUtil
import com.quickdaily.util.FileUtil
import com.quickdaily.util.VaultPathUtil

/** 切换 ```tasks 查询结果中的任务勾选状态并写回源文件。 */
object TasksQueryToggleUseCase {

    /** 返回是否写回成功。 */
    fun toggle(context: Context, vaultPath: String, task: VaultTask): Boolean =
        toggleByLine(context, vaultPath, task.path, task.lineNumber, task.isDone)

    /** 按 vault 相对路径 + 行号直接切换，供小部件等无需完整 [VaultTask] 的调用方使用。 */
    fun toggleByLine(
        context: Context,
        vaultPath: String,
        relativePath: String,
        lineNumber: Int,
        isDone: Boolean,
    ): Boolean {
        val absPath = VaultPathUtil.resolve(vaultPath, relativePath) ?: run {
            BetaLogger.log("TasksQuery", "toggle aborted reason=resolve_failed path=$relativePath")
            return false
        }
        val succeeded = FileUtil.withPathMutation(absPath) {
            toggleLocked(context, absPath, lineNumber, isDone)
        }
        if (succeeded) {
            // 从未完成 → 完成时播放完成音（与任务小部件行为一致）
            if (!isDone && TaskCompletionSoundPolicy.shouldPlay(
                    enabled = context.getSharedPreferences("QuickDaily", 0).getBoolean(
                        TaskCompletionSoundPolicy.PREF_KEY,
                        TaskCompletionSoundPolicy.DEFAULT_ENABLED,
                    ),
                    saveSucceeded = true,
                )
            ) {
                runCatching { MediaActionSound().play(MediaActionSound.FOCUS_COMPLETE) }
            }
            WidgetRefreshHelper.refreshAll(context)
        }
        return succeeded
    }

    private fun toggleLocked(context: Context, absPath: String, lineNumber: Int, isDone: Boolean): Boolean {
        val content = FileUtil.read(absPath)
        val parsed = ContentUtil.parseFrontmatter(content)
        val body = if (parsed.hasFrontmatter) parsed.body else content
        val lines = body.lines().toMutableList()
        if (lineNumber !in lines.indices) {
            BetaLogger.log("TasksQuery", "toggle aborted reason=line_out_of_range path=$absPath line=$lineNumber")
            return false
        }
        val toggled = toggleCheckbox(lines[lineNumber], isDone) ?: return false
        val savedLine = if (isDone) {
            TaskCompletionTimestampPolicy.removeIfPresent(toggled)
        } else {
            val enabled = context.getSharedPreferences("QuickDaily", 0).getBoolean(
                TaskCompletionTimestampPolicy.PREF_KEY,
                TaskCompletionTimestampPolicy.DEFAULT_ENABLED,
            )
            TaskCompletionTimestampPolicy.appendIfEnabled(
                line = toggled,
                enabled = enabled,
                date = DateUtil.todayStr(TaskCompletionTimestampPolicy.DATE_FORMAT),
            )
        }
        lines[lineNumber] = savedLine
        val newBody = lines.joinToString("\n")
        val saveContent = if (parsed.hasFrontmatter) {
            ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, newBody)
        } else {
            newBody
        }
        val ok = FileUtil.write(absPath, saveContent)
        if (!ok) {
            BetaLogger.log("TasksQuery", "toggle failed to write path=$absPath line=$lineNumber")
        }
        return ok
    }

    /** 将任务行的勾选框在 完成/未完成 之间切换，保留缩进与描述。 */
    fun toggleCheckbox(line: String, isDone: Boolean): String? {
        val open = line.indexOf('[')
        val close = if (open >= 0) line.indexOf(']', open + 1) else -1
        if (open < 0 || close < 0) return null
        val marker = if (isDone) " " else "x"
        return line.substring(0, open + 1) + marker + line.substring(close)
    }
}
