package com.quickdaily.tasks

import com.quickdaily.util.ContentUtil
import com.quickdaily.util.VaultPathUtil
import java.io.File

/**
 * 扫描 Obsidian vault 中的全部 `.md` 文件，抽取任务及其元数据。
 * 带进程内缓存（与 [com.quickdaily.util.TagScanner] 一致），避免预览反复扫描。
 */
object VaultTaskScanner {

    private const val MAX_SCAN_DEPTH = 15
    private const val CACHE_TTL_MS = 60_000L

    private var cachedTasks: List<VaultTask>? = null
    private var cachedVaultPath: String = ""
    private var cacheTime: Long = 0L

    private val TASK_LINE_RE = Regex("^([ \\t]*)([-+*]|\\d+[.)])\\s*\\[\\s*(.)\\s*]\\s*(.*)$")
    private val HEADING_RE = Regex("^#{1,6}\\s+(.*)$")

    fun getTasks(vaultPath: String): List<VaultTask> {
        if (vaultPath.isBlank()) return emptyList()
        val now = System.currentTimeMillis()
        if (cachedTasks != null && cachedVaultPath == vaultPath && now - cacheTime < CACHE_TTL_MS) {
            return cachedTasks!!
        }
        val tasks = scan(vaultPath)
        cachedTasks = tasks
        cachedVaultPath = vaultPath
        cacheTime = now
        return tasks
    }

    fun invalidateCache() {
        cachedTasks = null
    }

    private fun scan(vaultPath: String): List<VaultTask> {
        val vaultDir = File(vaultPath)
        if (!vaultDir.isDirectory || !vaultDir.exists()) return emptyList()

        val result = mutableListOf<VaultTask>()
        val mdFiles = vaultDir.walkTopDown()
            .maxDepth(MAX_SCAN_DEPTH)
            .filter { file ->
                if (file.isHidden) return@filter false
                if (file.parent?.let { File(it).isHidden } == true) return@filter false
                file.isFile && file.name.endsWith(".md", ignoreCase = true)
            }
            .toList()

        for (file in mdFiles) {
            try {
                val content = file.readText(Charsets.UTF_8)
                val parsed = ContentUtil.parseFrontmatter(content)
                val body = if (parsed.hasFrontmatter) parsed.body else content
                val relativePath = VaultPathUtil.relativePath(vaultPath, file.absolutePath) ?: file.name
                result.addAll(parseTasks(body, relativePath, file.nameWithoutExtension))
            } catch (_: Exception) {
                // 跳过不可读 / 损坏文件
            }
        }
        return result
    }

    /** 解析单个文档 body 中的任务行。 */
    fun parseTasks(body: String, path: String, filename: String): List<VaultTask> {
        val result = mutableListOf<VaultTask>()
        var heading: String? = null
        body.lines().forEachIndexed { index, line ->
            val headingMatch = HEADING_RE.matchEntire(line.trim())
            if (headingMatch != null) {
                heading = headingMatch.groupValues[1].trim()
                return@forEachIndexed
            }
            val match = TASK_LINE_RE.matchEntire(line) ?: return@forEachIndexed
            val statusSymbol = match.groupValues[3].getOrNull(0) ?: ' '
            val description = match.groupValues[4]
            result += VaultTask(
                description = description,
                statusSymbol = statusSymbol,
                line = line,
                lineNumber = index,
                path = path,
                filename = filename,
                dueDate = TaskDateParsing.extractDue(description),
                scheduledDate = TaskDateParsing.extractScheduled(description),
                startDate = TaskDateParsing.extractStart(description),
                doneDate = TaskDateParsing.extractDone(description),
                createdDate = TaskDateParsing.extractCreated(description),
                tags = TaskDateParsing.extractTags(description),
                priority = TaskDateParsing.extractPriority(description),
                heading = heading,
            )
        }
        return result
    }
}
