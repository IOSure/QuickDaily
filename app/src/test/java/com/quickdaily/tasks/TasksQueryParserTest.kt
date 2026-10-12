package com.quickdaily.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TasksQueryParserTest {

    private val today = LocalDate.of(2026, 10, 11)

    @Test
    fun parsesStatusFilters() {
        val q = TasksQueryParser.parse("not done", today)
        assertEquals(listOf(TaskFilter.Status(false)), q.filters)
    }

    @Test
    fun parsesDueToday() {
        val q = TasksQueryParser.parse("due today", today)
        assertEquals(1, q.filters.size)
        val f = q.filters[0] as TaskFilter.Date
        assertEquals(DateField.DUE, f.field)
        assertEquals(DateOp.ON, f.op)
        assertEquals(DateQuery.Single(today), f.value)
    }

    @Test
    fun parsesDueBeforeTomorrow() {
        val q = TasksQueryParser.parse("due before tomorrow", today)
        val f = q.filters[0] as TaskFilter.Date
        assertEquals(DateOp.BEFORE, f.op)
        assertEquals(DateQuery.Single(today.plusDays(1)), f.value)
    }

    @Test
    fun parsesDueRange() {
        val q = TasksQueryParser.parse("due 2026-10-01 2026-10-31", today)
        val f = q.filters[0] as TaskFilter.Date
        assertEquals(DateQuery.Range(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), f.value)
    }

    @Test
    fun parsesNoDueDateAndHasDueDate() {
        assertEquals(TaskFilter.HasDate(DateField.DUE, false), TasksQueryParser.parse("no due date", today).filters[0])
        assertEquals(TaskFilter.HasDate(DateField.DUE, true), TasksQueryParser.parse("has due date", today).filters[0])
    }

    @Test
    fun parsesTagsInclude() {
        val q = TasksQueryParser.parse("tags include #work #home", today)
        assertEquals(TaskFilter.Tags(true, listOf("work", "home")), q.filters[0])
    }

    @Test
    fun parsesSortGroupLimit() {
        val q = TasksQueryParser.parse("sort by due reverse\ngroup by due\nlimit 10", today)
        assertEquals(listOf(SortInstruction(SortField.DUE, reverse = true)), q.sorts)
        assertEquals(listOf(GroupInstruction(GroupField.DUE)), q.groups)
        assertEquals(10, q.limit)
    }

    @Test
    fun ignoresCommentsAndBlankLines() {
        val q = TasksQueryParser.parse("# 注释\n\nnot done\n", today)
        assertEquals(1, q.filters.size)
    }

    @Test
    fun parsesPriorityAbove() {
        val q = TasksQueryParser.parse("priority is above medium", today)
        assertEquals(TaskFilter.Priority(PriorityOp.ABOVE, 2), q.filters[0])
    }

    @Test
    fun parsesStatusType() {
        val q = TasksQueryParser.parse("status.type is not done", today)
        val f = q.filters[0] as TaskFilter.StatusTypeFilter
        assertEquals(StatusType.DONE, f.type)
        assertTrue(f.isNot)
    }

    @Test
    fun unknownInstructionIsIgnored() {
        val q = TasksQueryParser.parse("filter by function task.urgency > 5", today)
        assertNotNull(q)
        assertTrue(q.filters.isEmpty())
    }
}
