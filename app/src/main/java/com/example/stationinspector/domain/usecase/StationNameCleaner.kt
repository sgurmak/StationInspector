package com.example.stationinspector.domain.usecase

/**
 * Normalizes raw Czech railway station names imported from CSV into the short,
 * human-friendly form shown in the app.
 *
 * The algorithm (order matters):
 *  1. Extract and protect a parenthetical suffix, e.g. "(zastávka)".
 *  2. Strip domain abbreviations ("žst.", "os.n.").
 *  3. Truncate at the first dash separator ("- " or " - ").
 *  4. Re-attach the parentheses and collapse repeated whitespace.
 *
 * Pure and deterministic — no Android or I/O dependencies — so it is fully
 * unit-testable.
 */
object StationNameCleaner {

    private val PAREN_REGEX = "\\((.*?)\\)".toRegex()
    private val WHITESPACE_REGEX = "\\s+".toRegex()

    fun clean(rawName: String): String {
        var name = rawName
        var parentheses = ""

        // Step 1: extract and protect parenthetical suffixes
        val parenMatch = PAREN_REGEX.find(name)
        if (parenMatch != null) {
            parentheses = parenMatch.value
            name = name.replace(parenMatch.value, "")
        }

        // Step 2: strip domain-specific abbreviations
        name = name.replace("žst.", "").replace("os.n.", "")

        // Step 3: truncate at the first dash separator (" - " or "- ")
        val dashSpaceIndex = name.indexOf("- ")
        val spaceDashSpaceIndex = name.indexOf(" - ")

        val cutIndex = when {
            dashSpaceIndex != -1 && spaceDashSpaceIndex != -1 -> minOf(dashSpaceIndex, spaceDashSpaceIndex)
            dashSpaceIndex != -1 -> dashSpaceIndex
            spaceDashSpaceIndex != -1 -> spaceDashSpaceIndex
            else -> -1
        }

        if (cutIndex != -1) {
            name = name.substring(0, cutIndex)
        }

        // Step 4: re-attach parentheses; collapse whitespace
        if (parentheses.isNotEmpty()) {
            name = "$name $parentheses"
        }

        return name.replace(WHITESPACE_REGEX, " ").trim()
    }
}
