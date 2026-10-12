package com.quickdaily.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskDateParsingTest {

    @Test
    fun extractsTaskMetadata() {
        val tasks = VaultTaskScanner.parseTasks(
            """
            ## 计划
            - [ ] 写报告 📅 2026-10-15 ⏫ #work #daily
            - [x] 已完成 ✅ 2026-10-10
            """.trimIndent(),
            "Notes/a.md",
            "a",
        )

        assertEquals(2, tasks.size)

        val first = tasks[0]
        assertEquals("写报告 📅 2026-10-15 ⏫ #work #daily", first.description)
        assertEquals(LocalDate.of(2026, 10, 15), first.dueDate)
        assertEquals(1, first.priority)
        assertEquals(listOf("work", "daily"), first.tags)
        assertEquals("计划", first.heading)
        assertEquals("Notes/a.md", first.path)
        assertFalse(first.isDone)

        val second = tasks[1]
        assertTrue(second.isDone)
        assertEquals(LocalDate.of(2026, 10, 10), second.doneDate)
    }

    @Test
    fun parsesThisWeekRange() {
        val today = LocalDate.of(2026, 10, 11) // Sunday
        val range = TaskDateParsing.parseQueryDateExpr(listOf("this", "week"), today) as DateQuery.Range
        assertEquals(LocalDate.of(2026, 10, 5), range.start) // Monday
        assertEquals(LocalDate.of(2026, 10, 11), range.end)   // Sunday
    }

    @Test
    fun parsesSingleRelativeDates() {
        val today = LocalDate.of(2026, 10, 11)
        assertEquals(DateQuery.Single(today), TaskDateParsing.parseQueryDateExpr(listOf("today"), today))
        assertEquals(DateQuery.Single(today.plusDays(1)), TaskDateParsing.parseQueryDateExpr(listOf("tomorrow"), today))
        assertEquals(DateQuery.Single(today.minusDays(1)), TaskDateParsing.parseQueryDateExpr(listOf("yesterday"), today))
    }
}
