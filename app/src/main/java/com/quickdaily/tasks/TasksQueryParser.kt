package com.quickdaily.tasks

import java.time.LocalDate

/**
 * 解析 ```tasks 代码块中的查询指令，输出结构化 [ParsedQuery]。
 *
 * 说明：指令不区分大小写；行与行之间按 AND 语义组合。
 */
object TasksQueryParser {

    private val SORT_FIELDS = mapOf(
        "status" to SortField.STATUS,
        "status.type" to SortField.STATUS_TYPE,
        "due" to SortField.DUE,
        "done" to SortField.DONE,
        "scheduled" to SortField.SCHEDULED,
        "start" to SortField.START,
        "created" to SortField.CREATED,
        "happens" to SortField.HAPPENS,
        "description" to SortField.DESCRIPTION,
        "priority" to SortField.PRIORITY,
        "path" to SortField.PATH,
        "filename" to SortField.FILENAME,
        "heading" to SortField.HEADING,
        "tag" to SortField.TAG,
        "tags" to SortField.TAGS,
    )

    private val GROUP_FIELDS = mapOf(
        "status" to GroupField.STATUS,
        "due" to GroupField.DUE,
        "done" to GroupField.DONE,
        "scheduled" to GroupField.SCHEDULED,
        "start" to GroupField.START,
        "created" to GroupField.CREATED,
        "happens" to GroupField.HAPPENS,
        "priority" to GroupField.PRIORITY,
        "path" to GroupField.PATH,
        "filename" to GroupField.FILENAME,
        "heading" to GroupField.HEADING,
        "tags" to GroupField.TAGS,
    )

    private val PRIORITY_LEVELS = mapOf(
        "highest" to 0, "high" to 1, "medium" to 2, "low" to 3, "lowest" to 4,
    )

    fun parse(queryText: String, today: LocalDate = LocalDate.now()): ParsedQuery {
        val filters = mutableListOf<TaskFilter>()
        val sorts = mutableListOf<SortInstruction>()
        val groups = mutableListOf<GroupInstruction>()
        var limit: Int? = null

        for (rawLine in queryText.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val lower = line.lowercase()

            when {
                lower.startsWith("limit ") ->
                    limit = lower.substringAfter("limit ").trim().toIntOrNull()
                lower.startsWith("sort by ") ->
                    parseSort(lower.substringAfter("sort by ").trim())?.let { sorts.add(it) }
                lower.startsWith("group by ") ->
                    parseGroup(lower.substringAfter("group by ").trim())?.let { groups.add(it) }
                isNoOp(lower) -> Unit
                lower == "done" -> filters.add(TaskFilter.Status(true))
                lower == "not done" -> filters.add(TaskFilter.Status(false))
                else -> parseFilterLine(lower, today)?.let { filters.add(it) }
            }
        }
        return ParsedQuery(filters = filters, sorts = sorts, groups = groups, limit = limit)
    }

    private fun parseSort(rest: String): SortInstruction? {
        var s = rest
        var reverse = false
        if (s.endsWith(" reverse")) {
            reverse = true
            s = s.removeSuffix(" reverse").trim()
        }
        val field = SORT_FIELDS[s] ?: return null
        return SortInstruction(field, reverse)
    }

    private fun parseGroup(rest: String): GroupInstruction? {
        val field = GROUP_FIELDS[rest.trim()] ?: return null
        return GroupInstruction(field)
    }

    private fun isNoOp(lower: String): Boolean =
        lower == "explain" ||
            lower == "short mode" ||
            lower == "full mode" ||
            lower == "exclude sub-items" ||
            lower == "ignore global query" ||
            lower.startsWith("hide ") ||
            lower.startsWith("show ")

    private fun parseFilterLine(lower: String, today: LocalDate): TaskFilter? {
        // 无/有日期
        for (field in DateField.entries) {
            if (lower == "no ${field.keyword} date") return TaskFilter.HasDate(field, false)
            if (lower == "has ${field.keyword} date") return TaskFilter.HasDate(field, true)
        }
        // 日期过滤：<field> <op> <date>
        for (field in DateField.entries) {
            val prefix = "${field.keyword} "
            if (lower.startsWith(prefix)) {
                return parseDateFilter(field, lower.substring(prefix.length).trim(), today)
            }
        }
        // 状态类型
        if (lower.startsWith("status.type ")) return parseStatusType(lower.substringAfter("status.type ").trim())
        // tags 过滤
        if (lower.startsWith("tags include ")) {
            return TaskFilter.Tags(true, extractTagList(lower.substringAfter("tags include ")))
        }
        if (lower.startsWith("tags do not include ")) {
            return TaskFilter.Tags(false, extractTagList(lower.substringAfter("tags do not include ")))
        }
        if (lower.startsWith("tag includes ")) {
            return TaskFilter.TagContains(lower.substringAfter("tag includes ").trim(), true)
        }
        if (lower.startsWith("tag does not include ")) {
            return TaskFilter.TagContains(lower.substringAfter("tag does not include ").trim(), false)
        }
        // 文本过滤
        if (lower.startsWith("path includes ")) {
            return TaskFilter.PathContains(lower.substringAfter("path includes ").trim(), true)
        }
        if (lower.startsWith("path does not include ")) {
            return TaskFilter.PathContains(lower.substringAfter("path does not include ").trim(), false)
        }
        if (lower.startsWith("filename includes ")) {
            return TaskFilter.FilenameContains(lower.substringAfter("filename includes ").trim(), true)
        }
        if (lower.startsWith("filename does not include ")) {
            return TaskFilter.FilenameContains(lower.substringAfter("filename does not include ").trim(), false)
        }
        if (lower.startsWith("description includes ")) {
            return TaskFilter.DescriptionContains(lower.substringAfter("description includes ").trim(), true)
        }
        if (lower.startsWith("description does not include ")) {
            return TaskFilter.DescriptionContains(lower.substringAfter("description does not include ").trim(), false)
        }
        if (lower.startsWith("heading includes ")) {
            return TaskFilter.HeadingContains(lower.substringAfter("heading includes ").trim(), true)
        }
        if (lower.startsWith("heading does not include ")) {
            return TaskFilter.HeadingContains(lower.substringAfter("heading does not include ").trim(), false)
        }
        // 优先级
        if (lower.startsWith("priority ")) {
            return parsePriority(lower.substringAfter("priority ").trim())
        }
        return null
    }

    private fun parseStatusType(rest: String): TaskFilter.StatusTypeFilter? {
        val neg = rest.startsWith("is not ")
        val typeStr = if (neg) rest.removePrefix("is not ").trim() else rest.removePrefix("is ").trim()
        val type = StatusType.entries.firstOrNull { it.name.equals(typeStr, ignoreCase = true) } ?: return null
        return TaskFilter.StatusTypeFilter(type, neg)
    }

    private fun parseDateFilter(field: DateField, rest: String, today: LocalDate): TaskFilter.Date? {
        val tokens = rest.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        var op = DateOp.ON
        var i = 0
        if (tokens.size >= i + 3) {
            val triple = "${tokens[i]} ${tokens[i + 1]} ${tokens[i + 2]}"
            when (triple) {
                "on or before" -> { op = DateOp.ON_OR_BEFORE; i += 3 }
                "on or after" -> { op = DateOp.ON_OR_AFTER; i += 3 }
                "in or before" -> { op = DateOp.IN_OR_BEFORE; i += 3 }
                "in or after" -> { op = DateOp.IN_OR_AFTER; i += 3 }
            }
        }
        if (i == 0) {
            when (tokens[0]) {
                "on" -> { op = DateOp.ON; i = 1 }
                "in" -> { op = DateOp.IN; i = 1 }
                "before" -> { op = DateOp.BEFORE; i = 1 }
                "after" -> { op = DateOp.AFTER; i = 1 }
            }
        }
        val dateTokens = tokens.subList(i, tokens.size)
        val dateExpr = TaskDateParsing.parseQueryDateExpr(dateTokens, today) ?: return null
        return TaskFilter.Date(field, op, dateExpr)
    }

    private fun parsePriority(rest: String): TaskFilter.Priority? {
        val tokens = rest.split(Regex("\\s+")).filter { it.isNotBlank() }
        fun levelAt(idx: Int): Int? = PRIORITY_LEVELS[tokens.getOrNull(idx)]
        return when {
            tokens.size >= 3 && tokens[0] == "is" && tokens[1] == "not" && tokens[2] == "above" ->
                levelAt(3)?.let { TaskFilter.Priority(PriorityOp.NOT_ABOVE, it) }
            tokens.size >= 3 && tokens[0] == "is" && tokens[1] == "not" && tokens[2] == "below" ->
                levelAt(3)?.let { TaskFilter.Priority(PriorityOp.NOT_BELOW, it) }
            tokens.size >= 2 && tokens[0] == "is" && tokens[1] == "not" ->
                levelAt(2)?.let { TaskFilter.Priority(PriorityOp.IS_NOT, it) }
            tokens.size >= 2 && tokens[0] == "is" && tokens[1] == "above" ->
                levelAt(2)?.let { TaskFilter.Priority(PriorityOp.ABOVE, it) }
            tokens.size >= 2 && tokens[0] == "is" && tokens[1] == "below" ->
                levelAt(2)?.let { TaskFilter.Priority(PriorityOp.BELOW, it) }
            tokens.size >= 1 && tokens[0] == "is" ->
                levelAt(1)?.let { TaskFilter.Priority(PriorityOp.IS, it) }
            else -> null
        }
    }

    private fun extractTagList(s: String): List<String> =
        s.split(Regex("\\s+")).map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }
}
