package com.quickdaily.tasks

import java.time.LocalDate

/** 执行查询：过滤 → 排序 → 限制 → 分组。 */
object TasksQueryEngine {

    fun execute(
        query: ParsedQuery,
        tasks: List<VaultTask>,
        today: LocalDate = LocalDate.now(),
    ): List<TaskGroup> {
        // 1. 过滤（行与行之间 AND）
        var result = tasks.filter { task -> query.filters.all { matches(it, task) } }

        // 2. 排序（稳定排序；前面的 sort by 优先级更高）
        if (query.sorts.isEmpty()) {
            result = result.sortedWith(comparatorFor(SortInstruction(SortField.DUE)))
        } else {
            for (sort in query.sorts.asReversed()) {
                result = result.sortedWith(comparatorFor(sort))
            }
        }

        // 3. 限制总数
        val limit = query.limit
        if (limit != null && limit >= 0) {
            result = result.take(limit)
        }

        // 4. 分组
        return group(result, query.groups)
    }

    private fun matches(filter: TaskFilter, task: VaultTask): Boolean = when (filter) {
        is TaskFilter.Status -> task.isDone == filter.done
        is TaskFilter.StatusTypeFilter -> (task.statusType == filter.type) != filter.isNot
        is TaskFilter.Date -> dateMatches(task.dateFor(filter.field), filter.op, filter.value)
        is TaskFilter.HasDate -> (task.dateFor(filter.field) != null) == filter.has
        is TaskFilter.Tags -> {
            val taskTags = task.tags
            if (filter.include) filter.tags.all { it in taskTags } else filter.tags.none { it in taskTags }
        }
        is TaskFilter.TagContains -> {
            val any = task.tags.any { it.contains(filter.text, ignoreCase = true) }
            if (filter.include) any else !any
        }
        is TaskFilter.PathContains -> containsMatch(task.path, filter.text, filter.include)
        is TaskFilter.FilenameContains -> containsMatch(task.filename, filter.text, filter.include)
        is TaskFilter.DescriptionContains -> containsMatch(task.description, filter.text, filter.include)
        is TaskFilter.HeadingContains -> containsMatch(task.heading.orEmpty(), filter.text, filter.include)
        is TaskFilter.Priority -> priorityMatches(task.priority, filter)
    }

    private fun dateMatches(taskDate: LocalDate?, op: DateOp, value: DateQuery): Boolean {
        if (taskDate == null) return false
        return when (value) {
            is DateQuery.Single -> {
                val d = value.date
                when (op) {
                    DateOp.ON, DateOp.IN -> taskDate == d
                    DateOp.BEFORE -> taskDate < d
                    DateOp.AFTER -> taskDate > d
                    DateOp.ON_OR_BEFORE, DateOp.IN_OR_BEFORE -> taskDate <= d
                    DateOp.ON_OR_AFTER, DateOp.IN_OR_AFTER -> taskDate >= d
                }
            }
            is DateQuery.Range -> {
                when (op) {
                    DateOp.ON, DateOp.IN -> taskDate >= value.start && taskDate <= value.end
                    DateOp.BEFORE -> taskDate < value.start
                    DateOp.AFTER -> taskDate > value.end
                    DateOp.ON_OR_BEFORE, DateOp.IN_OR_BEFORE -> taskDate <= value.end
                    DateOp.ON_OR_AFTER, DateOp.IN_OR_AFTER -> taskDate >= value.start
                }
            }
        }
    }

    private fun containsMatch(value: String, text: String, include: Boolean): Boolean {
        val found = value.contains(text, ignoreCase = true)
        return if (include) found else !found
    }

    private fun priorityMatches(priority: Int?, filter: TaskFilter.Priority): Boolean {
        val level = filter.level
        return when (filter.op) {
            PriorityOp.IS -> priority == level
            PriorityOp.IS_NOT -> priority != level
            PriorityOp.ABOVE -> priority != null && priority < level
            PriorityOp.BELOW -> priority != null && priority > level
            PriorityOp.NOT_ABOVE -> priority == null || priority >= level
            PriorityOp.NOT_BELOW -> priority == null || priority <= level
        }
    }

    private fun comparatorFor(sort: SortInstruction): Comparator<VaultTask> {
        val comparator = when (sort.field) {
            SortField.STATUS -> Comparator { a, b -> a.isDone.compareTo(b.isDone) }
            SortField.STATUS_TYPE -> Comparator { a, b -> statusTypeOrder(a.statusType).compareTo(statusTypeOrder(b.statusType)) }
            SortField.DUE -> dateComparator { it.dueDate }
            SortField.DONE -> dateComparator { it.doneDate }
            SortField.SCHEDULED -> dateComparator { it.scheduledDate }
            SortField.START -> dateComparator { it.startDate }
            SortField.CREATED -> dateComparator { it.createdDate }
            SortField.HAPPENS -> dateComparator { it.happensDate }
            SortField.DESCRIPTION -> stringComparator { it.description }
            SortField.PRIORITY -> priorityComparator()
            SortField.PATH -> stringComparator { it.path }
            SortField.FILENAME -> stringComparator { it.filename }
            SortField.HEADING -> stringComparator { it.heading.orEmpty() }
            SortField.TAG -> stringComparator { it.tags.firstOrNull().orEmpty() }
            SortField.TAGS -> stringComparator { it.tags.sorted().joinToString(",") }
        }
        return if (sort.reverse) comparator.reversed() else comparator
    }

    private fun dateComparator(get: (VaultTask) -> LocalDate?): Comparator<VaultTask> =
        Comparator { a, b ->
            val da = get(a)
            val db = get(b)
            when {
                da == null && db == null -> 0
                da == null -> 1
                db == null -> -1
                else -> da.compareTo(db)
            }
        }

    private fun stringComparator(get: (VaultTask) -> String): Comparator<VaultTask> =
        Comparator { a, b -> get(a).lowercase().compareTo(get(b).lowercase()) }

    private fun priorityComparator(): Comparator<VaultTask> =
        Comparator { a, b ->
            val pa = a.priority
            val pb = b.priority
            when {
                pa == null && pb == null -> 0
                pa == null -> 1
                pb == null -> -1
                else -> pa.compareTo(pb)
            }
        }

    private fun statusTypeOrder(type: StatusType): Int = when (type) {
        StatusType.IN_PROGRESS -> 0
        StatusType.TODO -> 1
        StatusType.DONE -> 3
        StatusType.CANCELLED -> 4
        StatusType.NON_TASK -> 5
    }

    private fun group(tasks: List<VaultTask>, groups: List<GroupInstruction>): List<TaskGroup> {
        if (groups.isEmpty()) return listOf(TaskGroup(null, tasks))
        val field = groups.first().field
        val keyed = LinkedHashMap<String, MutableList<VaultTask>>()
        fun add(key: String, task: VaultTask) {
            keyed.getOrPut(key) { mutableListOf() }.add(task)
        }
        when (field) {
            GroupField.STATUS -> tasks.forEach { add(if (it.isDone) "Done" else "Todo", it) }
            GroupField.DUE -> tasks.forEach { add(dateGroupHeading(it.dueDate, "due"), it) }
            GroupField.DONE -> tasks.forEach { add(dateGroupHeading(it.doneDate, "done"), it) }
            GroupField.SCHEDULED -> tasks.forEach { add(dateGroupHeading(it.scheduledDate, "scheduled"), it) }
            GroupField.START -> tasks.forEach { add(dateGroupHeading(it.startDate, "start"), it) }
            GroupField.CREATED -> tasks.forEach { add(dateGroupHeading(it.createdDate, "created"), it) }
            GroupField.HAPPENS -> tasks.forEach { add(dateGroupHeading(it.happensDate, "happens"), it) }
            GroupField.PRIORITY -> tasks.forEach { add(priorityHeading(it.priority), it) }
            GroupField.PATH -> tasks.forEach { add(it.path, it) }
            GroupField.FILENAME -> tasks.forEach { add(it.filename, it) }
            GroupField.HEADING -> tasks.forEach { add(it.heading ?: "(No heading)", it) }
            GroupField.TAGS -> tasks.forEach { task ->
                if (task.tags.isEmpty()) add("(No tags)", task)
                else task.tags.sorted().forEach { add(it, task) }
            }
        }
        return keyed.map { (heading, list) -> TaskGroup(heading, list) }
    }

    private fun dateGroupHeading(date: LocalDate?, field: String): String =
        date?.toString() ?: "No $field date"

    private fun priorityHeading(priority: Int?): String = when (priority) {
        0 -> "Highest"
        1 -> "High"
        2 -> "Medium"
        3 -> "Low"
        4 -> "Lowest"
        else -> "None"
    }
}
