package com.quickdaily.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TasksQueryToggleUseCaseTest {

    @Test
    fun togglesTodoToDone() {
        assertEquals("- [x] 任务", TasksQueryToggleUseCase.toggleCheckbox("- [ ] 任务", isDone = false))
    }

    @Test
    fun togglesDoneToTodo() {
        assertEquals("- [ ] 任务", TasksQueryToggleUseCase.toggleCheckbox("- [x] 任务", isDone = true))
    }

    @Test
    fun cancelledBecomesTodo() {
        assertEquals("- [ ] 已取消", TasksQueryToggleUseCase.toggleCheckbox("- [-] 已取消", isDone = true))
    }

    @Test
    fun inProgressBecomesDone() {
        assertEquals("- [x] 进行中", TasksQueryToggleUseCase.toggleCheckbox("- [/] 进行中", isDone = false))
    }

    @Test
    fun preservesIndentationAndDescription() {
        assertEquals("\t- [x] 子任务 #tag", TasksQueryToggleUseCase.toggleCheckbox("\t- [ ] 子任务 #tag", isDone = false))
    }

    @Test
    fun nonCheckboxLineReturnsNull() {
        assertNull(TasksQueryToggleUseCase.toggleCheckbox("普通文本", isDone = false))
    }
}
