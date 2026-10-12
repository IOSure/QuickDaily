package com.quickdaily.tasks

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TasksQueryEngineTest {

    private val today = LocalDate.of(2026, 10, 11)

    private fun task(
        description: String,
        done: Boolean = false,
        due: LocalDate? = null,
        tags: List<String> = emptyList(),
        path: String = "Notes/a.md",
        filename: String = "a",
    ) = VaultTask(
        description = description,
        statusSymbol = if (done) 'x' else ' ',
        line = "- [${if (done) "x" else " "}] $description",
        lineNumber = 0,
        path = path,
        filename = filename,
        dueDate = due,
        tags = tags,
    )

    private fun run(query: String, tasks: List<VaultTask>): List<String> =
        TasksQueryEngine.execute(TasksQueryParser.parse(query, today), tasks, today)
            .flatMap { it.tasks }
            .map { it.description }

    @Test
    fun filtersNotDone() {
        val tasks = listOf(task("a"), task("b", done = true), task("c"))
        assertEquals(listOf("a", "c"), run("not done", tasks))
    }

    @Test
    fun filtersDone() {
        val tasks = listOf(task("a"), task("b", done = true))
        assertEquals(listOf("b"), run("done", tasks))
    }

    @Test
    fun filtersDueToday() {
        val tasks = listOf(
            task("due today", due = today),
            task("due tomorrow", due = today.plusDays(1)),
            task("no due"),
        )
        assertEquals(listOf("due today"), run("due today", tasks))
    }

    @Test
    fun filtersTagsInclude() {
        val tasks = listOf(
            task("work", tags = listOf("work")),
            task("home", tags = listOf("home")),
            task("both", tags = listOf("work", "home")),
        )
        assertEquals(listOf("work", "both"), run("tags include #work", tasks))
    }

    @Test
    fun filtersPathIncludes() {
        val tasks = listOf(
            task("in project", path = "Project/plan.md", filename = "plan"),
            task("elsewhere", path = "Notes/note.md", filename = "note"),
        )
        assertEquals(listOf("in project"), run("path includes project", tasks))
    }

    @Test
    fun sortsByDueEarliestFirstNullsLast() {
        val tasks = listOf(
            task("later", due = today.plusDays(5)),
            task("no due"),
            task("earlier", due = today.minusDays(1)),
        )
        assertEquals(listOf("earlier", "later", "no due"), run("sort by due", tasks))
    }

    @Test
    fun groupsByStatus() {
        val tasks = listOf(task("a"), task("b", done = true))
        val groups = TasksQueryEngine.execute(TasksQueryParser.parse("group by status", today), tasks, today)
        assertEquals(listOf("Todo", "Done"), groups.map { it.heading })
    }

    @Test
    fun appliesLimit() {
        val tasks = listOf(task("1"), task("2"), task("3"))
        assertEquals(2, run("limit 2", tasks).size)
    }
}
