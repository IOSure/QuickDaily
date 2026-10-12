package com.quickdaily.tasks

import java.time.LocalDate

/** 任务状态类型（对齐 Obsidian-Tasks 的概念）。 */
enum class StatusType { TODO, DONE, IN_PROGRESS, CANCELLED, NON_TASK }

fun statusTypeOf(symbol: Char): StatusType = when (symbol) {
    'x', 'X' -> StatusType.DONE
    '-' -> StatusType.CANCELLED
    '/' -> StatusType.IN_PROGRESS
    ' ' -> StatusType.TODO
    else -> StatusType.TODO
}

/** 从 vault 扫描出的单条任务及其元数据。 */
data class VaultTask(
    val description: String,
    val statusSymbol: Char,
    val line: String,
    val lineNumber: Int,
    val path: String,
    val filename: String,
    val dueDate: LocalDate? = null,
    val scheduledDate: LocalDate? = null,
    val startDate: LocalDate? = null,
    val doneDate: LocalDate? = null,
    val createdDate: LocalDate? = null,
    val tags: List<String> = emptyList(),
    val priority: Int? = null,
    val heading: String? = null,
) {
    val statusType: StatusType get() = statusTypeOf(statusSymbol)
    val isDone: Boolean get() =
        statusType == StatusType.DONE || statusType == StatusType.CANCELLED || statusType == StatusType.NON_TASK

    /** happens = 开始/计划/到期日期中最早的一个（同 Obsidian-Tasks）。 */
    val happensDate: LocalDate? get() = listOfNotNull(dueDate, scheduledDate, startDate).minOrNull()

    fun dateFor(field: DateField): LocalDate? = when (field) {
        DateField.DUE -> dueDate
        DateField.SCHEDULED -> scheduledDate
        DateField.START -> startDate
        DateField.DONE -> doneDate
        DateField.CREATED -> createdDate
        DateField.HAPPENS -> happensDate
    }
}

/** 任务上的日期字段。 */
enum class DateField(val keyword: String) {
    DUE("due"),
    SCHEDULED("scheduled"),
    START("start"),
    DONE("done"),
    CREATED("created"),
    HAPPENS("happens");

    companion object {
        fun fromKeyword(keyword: String): DateField? = entries.firstOrNull { it.keyword == keyword }
    }
}

/** 日期比较运算符。 */
enum class DateOp { ON, BEFORE, AFTER, ON_OR_BEFORE, ON_OR_AFTER, IN, IN_OR_BEFORE, IN_OR_AFTER }

/** 查询中出现的日期表达式：单个日期或日期区间。 */
sealed class DateQuery {
    data class Single(val date: LocalDate) : DateQuery()
    data class Range(val start: LocalDate, val end: LocalDate) : DateQuery()
}

/** 排序字段。 */
enum class SortField {
    STATUS, STATUS_TYPE, DUE, DONE, SCHEDULED, START, CREATED, HAPPENS, DESCRIPTION, PRIORITY, PATH, FILENAME, HEADING, TAG, TAGS
}

/** 分组字段。 */
enum class GroupField {
    STATUS, DUE, DONE, SCHEDULED, START, CREATED, HAPPENS, PRIORITY, PATH, FILENAME, HEADING, TAGS
}

/** 优先级过滤运算符。 */
enum class PriorityOp { IS, IS_NOT, ABOVE, BELOW, NOT_ABOVE, NOT_BELOW }

/** 单条过滤指令。 */
sealed class TaskFilter {
    /** done=true 表示 "done"，false 表示 "not done"。 */
    data class Status(val done: Boolean) : TaskFilter()
    data class StatusTypeFilter(val type: StatusType, val isNot: Boolean) : TaskFilter()
    data class Date(val field: DateField, val op: DateOp, val value: DateQuery) : TaskFilter()
    data class HasDate(val field: DateField, val has: Boolean) : TaskFilter()
    data class Tags(val include: Boolean, val tags: List<String>) : TaskFilter()
    data class TagContains(val text: String, val include: Boolean) : TaskFilter()
    data class PathContains(val text: String, val include: Boolean) : TaskFilter()
    data class FilenameContains(val text: String, val include: Boolean) : TaskFilter()
    data class DescriptionContains(val text: String, val include: Boolean) : TaskFilter()
    data class HeadingContains(val text: String, val include: Boolean) : TaskFilter()
    data class Priority(val op: PriorityOp, val level: Int) : TaskFilter()
}

data class SortInstruction(val field: SortField, val reverse: Boolean = false)
data class GroupInstruction(val field: GroupField)

/** 解析后的完整查询。 */
data class ParsedQuery(
    val filters: List<TaskFilter> = emptyList(),
    val sorts: List<SortInstruction> = emptyList(),
    val groups: List<GroupInstruction> = emptyList(),
    val limit: Int? = null,
)

/** 执行结果的一个分组。 */
data class TaskGroup(
    val heading: String?,
    val tasks: List<VaultTask>,
)
