package com.quickdaily.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quickdaily.BetaLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 渲染单个 ```tasks 查询块的结果，支持点击勾选并写回源文件。 */
@Composable
fun TasksQueryBlock(
    queryText: String,
    vaultPath: String?,
    modifier: Modifier = Modifier,
) {
    if (vaultPath.isNullOrBlank()) {
        BasicText(
            text = AnnotatedString("未配置 Vault 路径，无法执行任务查询。"),
            style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = modifier.padding(vertical = 2.dp),
        )
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }

    val groups by produceState(initialValue = emptyList<TaskGroup>(), vaultPath, queryText, refreshKey) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val query = TasksQueryParser.parse(queryText)
                val all = VaultTaskScanner.getTasks(vaultPath)
                TasksQueryEngine.execute(query, all)
            }.getOrElse { error ->
                BetaLogger.logException("TasksQuery", "execute_failed", error)
                emptyList()
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        var renderedAny = false
        groups.forEach { group ->
            if (group.heading != null) {
                BasicText(
                    text = AnnotatedString(group.heading),
                    style = LocalTextStyle.current.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            group.tasks.forEach { task ->
                renderedAny = true
                TaskRow(
                    task = task,
                    onClick = {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                TasksQueryToggleUseCase.toggle(context, vaultPath, task)
                            }
                            if (ok) {
                                VaultTaskScanner.invalidateCache()
                                refreshKey++
                            }
                        }
                    },
                )
            }
        }
        if (!renderedAny) {
            BasicText(
                text = AnnotatedString("(无匹配任务)"),
                style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun TaskRow(
    task: VaultTask,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        Icon(
            imageVector = if (task.isDone) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
            contentDescription = if (task.isDone) "已完成" else "未完成",
            tint = if (task.isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                text = AnnotatedString(task.description),
                style = LocalTextStyle.current.copy(
                    color = if (task.isDone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.onSurface,
                ),
            )
            val meta = buildString {
                task.dueDate?.let { append("📅 $it ") }
                task.scheduledDate?.let { append("⏳ $it ") }
                if (task.tags.isNotEmpty()) append(task.tags.joinToString(" ") { "#$it" })
            }
            if (meta.isNotBlank()) {
                BasicText(
                    text = AnnotatedString("$meta · ${task.path}"),
                    style = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    ),
                )
            }
        }
    }
}
