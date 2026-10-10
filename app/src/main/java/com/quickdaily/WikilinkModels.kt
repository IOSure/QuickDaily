package com.quickdaily

/** A page or frontmatter alias that can be inserted as an Obsidian wikilink. */
data class WikilinkCandidate(
    val targetPath: String,
    val alias: String? = null,
) {
    val queryText: String
        get() = alias ?: targetPath

    val displayText: String
        get() = alias ?: targetPath

    /** 文件名（不含目录），用于支持按文件名而不是完整路径做前缀匹配。 */
    val basename: String
        get() = targetPath.substringAfterLast('/')

    val insertionText: String
        get() = alias?.let { "[[$targetPath|$it]]" } ?: "[[$targetPath]]"

    val stableKey: String
        get() = if (alias == null) "page:$targetPath" else "alias:$targetPath\u001f$alias"
}

object WikilinkCandidatePolicy {
    const val DEFAULT_LIMIT = 3

    fun filter(
        candidates: List<WikilinkCandidate>,
        query: String,
        limit: Int = DEFAULT_LIMIT,
    ): List<WikilinkCandidate> {
        val prefix = query.trim()
        if (prefix.isEmpty()) return emptyList()
        return candidates
            .filter {
                it.queryText.startsWith(prefix, ignoreCase = true) ||
                    it.basename.startsWith(prefix, ignoreCase = true)
            }
            .sortedWith(
                compareBy<WikilinkCandidate> { candidate ->
                    // 完整路径/别名前缀匹配优先于文件名匹配
                    if (candidate.queryText.startsWith(prefix, ignoreCase = true)) 0 else 1
                }
                    .thenBy { it.queryText.length }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.queryText }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.targetPath }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.alias.orEmpty() },
            )
            .take(limit)
    }
}
