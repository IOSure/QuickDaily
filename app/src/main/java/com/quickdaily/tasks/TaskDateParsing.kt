package com.quickdaily.tasks

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * 负责两件事：
 * 1. 从任务行描述中抽取日期（📅 ⏳ 🛫 ✅ ➕）、优先级（🔺⏫🔼🔽⏬）与 tag。
 * 2. 解析查询里的日期表达式（绝对日期、相对日期、相对区间）。
 */
object TaskDateParsing {

    private val DUE_RE = Regex("📅\\s*(\\d{4}-\\d{2}-\\d{2})")
    private val SCHEDULED_RE = Regex("⏳\\s*(\\d{4}-\\d{2}-\\d{2})")
    private val START_RE = Regex("🛫\\s*(\\d{4}-\\d{2}-\\d{2})")
    private val DONE_RE = Regex("✅\\s*(\\d{4}-\\d{2}-\\d{2})")
    private val CREATED_RE = Regex("➕\\s*(\\d{4}-\\d{2}-\\d{2})")
    private val INLINE_TAG_RE = Regex("(?:^|\\s)#([\\w/\\-]+)")

    // 优先级 emoji 顺序即数值：0 最高 … 4 最低（含已废弃的 🔺 Highest）。
    private val PRIORITY_EMOJIS = listOf("🔺", "⏫", "🔼", "🔽", "⏬")

    private val WEEKDAYS = mapOf(
        "monday" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY,
    )

    // ── 任务行元数据抽取 ───────────────────────────────

    fun extractDue(text: String): LocalDate? = parseEmojiDate(text, DUE_RE)
    fun extractScheduled(text: String): LocalDate? = parseEmojiDate(text, SCHEDULED_RE)
    fun extractStart(text: String): LocalDate? = parseEmojiDate(text, START_RE)
    fun extractDone(text: String): LocalDate? = parseEmojiDate(text, DONE_RE)
    fun extractCreated(text: String): LocalDate? = parseEmojiDate(text, CREATED_RE)

    private fun parseEmojiDate(text: String, regex: Regex): LocalDate? =
        regex.find(text)?.groupValues?.getOrNull(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun extractPriority(text: String): Int? {
        var bestIndex = Int.MAX_VALUE
        var bestPriority: Int? = null
        PRIORITY_EMOJIS.forEachIndexed { priority, emoji ->
            val idx = text.indexOf(emoji)
            if (idx >= 0 && idx < bestIndex) {
                bestIndex = idx
                bestPriority = priority
            }
        }
        return bestPriority
    }

    fun extractTags(description: String): List<String> =
        INLINE_TAG_RE.findAll(description).map { it.groupValues[1] }.filter { it.isNotBlank() }.toList()

    // ── 查询日期表达式解析 ─────────────────────────────

    /** 解析一段日期表达式（可能为单个日期或区间）。 */
    fun parseQueryDateExpr(tokens: List<String>, today: LocalDate = LocalDate.now()): DateQuery? {
        if (tokens.isEmpty()) return null
        if (tokens.size == 2) {
            val d1 = parseAbsolute(tokens[0])
            val d2 = parseAbsolute(tokens[1])
            if (d1 != null && d2 != null) {
                return if (d1 <= d2) DateQuery.Range(d1, d2) else DateQuery.Range(d2, d1)
            }
            parseRelativeRange(tokens[0], tokens[1], today)?.let { return it }
            parseModifiedWeekday(tokens[0], tokens[1], today)?.let { return DateQuery.Single(it) }
        }
        parseSingleDate(tokens[0], today)?.let { return DateQuery.Single(it) }
        return null
    }

    fun parseAbsolute(s: String): LocalDate? =
        runCatching { LocalDate.parse(s) }.getOrNull()

    fun parseSingleDate(s: String, today: LocalDate = LocalDate.now()): LocalDate? {
        val t = s.lowercase().trim()
        return when (t) {
            "today" -> today
            "tomorrow" -> today.plusDays(1)
            "yesterday" -> today.minusDays(1)
            else -> parseAbsolute(s) ?: parseClosestWeekday(t, today)
        }
    }

    private fun parseClosestWeekday(name: String, today: LocalDate): LocalDate? {
        val dow = WEEKDAYS[name] ?: return null
        if (today.dayOfWeek == dow) return today
        val next = today.with(TemporalAdjusters.next(dow))
        val prev = today.with(TemporalAdjusters.previous(dow))
        val nextDiff = ChronoUnit.DAYS.between(today, next)
        val prevDiff = ChronoUnit.DAYS.between(prev, today)
        return if (nextDiff <= prevDiff) next else prev
    }

    private fun parseModifiedWeekday(modifier: String, name: String, today: LocalDate): LocalDate? {
        val dow = WEEKDAYS[name] ?: return null
        return when (modifier) {
            "next" -> today.with(TemporalAdjusters.next(dow))
            "last" -> today.with(TemporalAdjusters.previous(dow))
            else -> null
        }
    }

    /** 解析 this/last/next + week/month/quarter/year 为区间（含首尾）。 */
    private fun parseRelativeRange(first: String, second: String, today: LocalDate): DateQuery.Range? {
        fun base(unit: String): Pair<LocalDate, LocalDate>? {
            return when (unit) {
                "week" -> {
                    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val b = when (first) {
                        "this" -> monday; "next" -> monday.plusWeeks(1); "last" -> monday.minusWeeks(1); else -> return null
                    }
                    b to b.plusDays(6)
                }
                "month" -> {
                    val firstDay = today.withDayOfMonth(1)
                    val b = when (first) {
                        "this" -> firstDay; "next" -> firstDay.plusMonths(1); "last" -> firstDay.minusMonths(1); else -> return null
                    }
                    b to b.withDayOfMonth(b.lengthOfMonth())
                }
                "quarter" -> {
                    val startMonth = ((today.monthValue - 1) / 3) * 3 + 1
                    val qFirst = today.withDayOfMonth(1).withMonth(startMonth)
                    val b = when (first) {
                        "this" -> qFirst; "next" -> qFirst.plusMonths(3); "last" -> qFirst.minusMonths(3); else -> return null
                    }
                    b to b.plusMonths(3).minusDays(1)
                }
                "year" -> {
                    val yFirst = today.withDayOfYear(1)
                    val b = when (first) {
                        "this" -> yFirst; "next" -> yFirst.plusYears(1); "last" -> yFirst.minusYears(1); else -> return null
                    }
                    b to b.withDayOfYear(b.lengthOfYear())
                }
                else -> return null
            }
        }
        val (start, end) = base(second) ?: return null
        return DateQuery.Range(start, end)
    }
}
