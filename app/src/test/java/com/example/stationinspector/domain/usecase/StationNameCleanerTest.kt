package com.example.stationinspector.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [StationNameCleaner.clean].
 *
 * Each expected value below was derived by manually tracing the four-step
 * algorithm (extract parentheses -> strip abbreviations -> truncate at first
 * "- "/" - " -> re-attach parentheses + collapse whitespace).
 */
class StationNameCleanerTest {

    @Test
    fun `plain name without separators is unchanged`() {
        // No parens, no abbreviations, no dash separator -> only trim/collapse.
        assertEquals("Kolín", StationNameCleaner.clean("Kolín"))
    }

    @Test
    fun `truncates at space-dash-space separator`() {
        // "Praha - hlavní": " - " found at index 5 -> substring(0,5) = "Praha".
        assertEquals("Praha", StationNameCleaner.clean("Praha - hlavní"))
    }

    @Test
    fun `dash without trailing space is not a separator`() {
        // "Brno-město": no "- " (no space after dash) and no " - " -> kept as is.
        assertEquals("Brno-město", StationNameCleaner.clean("Brno-město"))
    }

    @Test
    fun `strips zst abbreviation`() {
        // "žst. Kolín": remove "žst." -> " Kolín"; no dash; trim -> "Kolín".
        assertEquals("Kolín", StationNameCleaner.clean("žst. Kolín"))
    }

    @Test
    fun `strips os_n abbreviation`() {
        // "Kolín os.n.": remove "os.n." -> "Kolín "; collapse+trim -> "Kolín".
        assertEquals("Kolín", StationNameCleaner.clean("Kolín os.n."))
    }

    @Test
    fun `parenthetical suffix is preserved and re-attached`() {
        // "Kolín (zastávka)": parens extracted, name becomes "Kolín ",
        // re-attach -> "Kolín  (zastávka)", collapse -> "Kolín (zastávka)".
        assertEquals("Kolín (zastávka)", StationNameCleaner.clean("Kolín (zastávka)"))
    }

    @Test
    fun `parentheses combined with dash separator`() {
        // "Plzeň (zastávka) - nádraží":
        //  step1 -> name="Plzeň  - nádraží", parens="(zastávka)"
        //  step3 -> indexOf("- ")=7, indexOf(" - ")=6 -> cut at 6 -> "Plzeň "
        //  step4 -> "Plzeň  (zastávka)" -> collapse/trim -> "Plzeň (zastávka)"
        assertEquals("Plzeň (zastávka)", StationNameCleaner.clean("Plzeň (zastávka) - nádraží"))
    }

    @Test
    fun `collapses multiple internal spaces`() {
        // "Praha    hlavní": no dash separator; whitespace collapsed to single.
        assertEquals("Praha hlavní", StationNameCleaner.clean("Praha    hlavní"))
    }

    @Test
    fun `empty string stays empty`() {
        assertEquals("", StationNameCleaner.clean(""))
    }

    @Test
    fun `leading and trailing whitespace is trimmed`() {
        assertEquals("Kolín", StationNameCleaner.clean("   Kolín   "))
    }

    @Test
    fun `zst abbreviation combined with dash separator`() {
        // "žst. Praha - hlavní": remove "žst." -> " Praha - hlavní";
        //  " - " at index 6 -> substring(0,6)=" Praha"; trim -> "Praha".
        assertEquals("Praha", StationNameCleaner.clean("žst. Praha - hlavní"))
    }
}
