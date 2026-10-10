package com.quickdaily

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorTextActionPolicyTest {
    @Test
    fun recommendedMarkdownActionsRemainHiddenByDefault() {
        val recommended = setOf(
            EditorToolbarAction.STRIKETHROUGH.id,
            EditorToolbarAction.INLINE_CODE.id,
            EditorToolbarAction.QUOTE.id,
            EditorToolbarAction.CODE_BLOCK.id,
            EditorToolbarAction.ORDERED_LIST.id,
            EditorToolbarAction.HORIZONTAL_RULE.id,
            EditorToolbarAction.MARKDOWN_LINK.id,
        )
        assertTrue(recommended.none { it in EditorToolbarPolicy.defaultVisible })
        assertTrue(recommended.all { it in EditorToolbarPolicy.defaultOrder.map(EditorToolbarAction::id) })
    }

    @Test
    fun delimiterWrapsSelectedTextAndTogglesAtCursor() {
        val wrapped = EditorTextActionPolicy.toggleDelimiter("hello", TextRange(0, 5), "~~")
        assertEquals("~~hello~~", wrapped.text)

        val removed = EditorTextActionPolicy.toggleDelimiter(wrapped.text, TextRange(2), "~~")
        assertEquals("hello", removed.text)
    }

    @Test
    fun quoteAndCodeBlockPreserveMarkdownShape() {
        val quoted = EditorTextActionPolicy.prefixLines("one\ntwo", TextRange(0, 7), "> ")
        assertEquals("> one\n> two", quoted.text)

        val code = EditorTextActionPolicy.codeBlock("body", TextRange(0, 4))
        assertEquals("```\nbody\n```", code.text)
    }

    @Test
    fun linkAndHorizontalRuleUseEditableInsertionPoints() {
        val link = EditorTextActionPolicy.markdownLink("title", TextRange(0, 5))
        assertEquals("[title](https://)", link.text)
        assertEquals(8, link.selection.start)

        val divider = EditorTextActionPolicy.horizontalRule("a", TextRange(1))
        assertEquals("a\n---\n", divider.text)
    }

    @Test
    fun movingTwoLinesPreservesTheirSeparator() {
        assertEquals("B\nA", EditorTextActionPolicy.moveLineUp("A\nB", TextRange(3)).text)
        assertEquals("B\nA", EditorTextActionPolicy.moveLineDown("A\nB", TextRange(0)).text)
    }

    @Test
    fun movingAtTheEdgesIsAStableNoOpAndMiddleLinesSwap() {
        assertEquals("A\nB\nC", EditorTextActionPolicy.moveLineUp("A\nB\nC", TextRange(0)).text)
        assertEquals("A\nB\nC", EditorTextActionPolicy.moveLineDown("A\nB\nC", TextRange(5)).text)
        assertEquals("B\nA\nC", EditorTextActionPolicy.moveLineUp("A\nB\nC", TextRange(3)).text)
        assertEquals("A\nC\nB", EditorTextActionPolicy.moveLineDown("A\nB\nC", TextRange(3)).text)
        assertEquals("B\nA\n", EditorTextActionPolicy.moveLineUp("A\nB\n", TextRange(3)).text)
    }

    @Test
    fun insertWikilinkPlacesCursorBetweenBrackets() {
        val result = EditorTextActionPolicy.insertWikilink("", TextRange(0))
        assertEquals("[[]]", result.text)
        // 光标位于 [[ 和 ]] 之间
        assertEquals(2, result.selection.start)
    }

    @Test
    fun cursorInsideEmptyWikilinkTriggersCompletion() {
        val result = EditorTextActionPolicy.insertWikilink("", TextRange(0))
        val trigger = WikilinkPolicy.trigger(result.text, result.selection.start)
        assertNotNull(trigger)
        assertEquals(0, trigger?.start)
        assertEquals(4, trigger?.replaceEnd)
        assertEquals("", trigger?.query)
    }

    @Test
    fun typingInsideBracketsKeepsCompletionActive() {
        // 模拟在 [[]] 中间输入 P：文本变为 [[P]]，光标在 P 之后
        val result = EditorTextActionPolicy.insertWikilink("", TextRange(0))
        val typed = result.text.substring(0, 2) + "P" + result.text.substring(2)
        val trigger = WikilinkPolicy.trigger(typed, 3)
        assertNotNull(trigger)
        assertEquals("P", trigger?.query)
        assertEquals(5, trigger?.replaceEnd)
    }
}
