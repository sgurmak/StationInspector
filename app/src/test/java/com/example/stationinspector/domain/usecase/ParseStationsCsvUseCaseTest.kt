package com.example.stationinspector.domain.usecase

import com.example.stationinspector.domain.model.StationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for [ParseStationsCsvUseCase].
 *
 * Format per line: `name<sep>date` where `<sep>` is `;` (preferred when
 * present) or `,`. Dates use `dd.MM.yyyy`; unparseable dates -> null (kept).
 * Names normalized via [StationNameCleaner] and deduplicated by cleaned name.
 */
class ParseStationsCsvUseCaseTest {

    private val parse = ParseStationsCsvUseCase()

    @Test
    fun `semicolon separator produces a single station`() {
        val result = parse("Kolín;01.02.2026")

        assertEquals(1, result.size)
        assertEquals("Kolín", result[0].name)
        assertEquals(LocalDate.of(2026, 2, 1), result[0].inspectionDate)
        assertEquals(StationStatus.PENDING, result[0].status)
    }

    @Test
    fun `comma separator used when no semicolon present`() {
        val result = parse("Kolín,01.02.2026")

        assertEquals(1, result.size)
        assertEquals("Kolín", result[0].name)
        assertEquals(LocalDate.of(2026, 2, 1), result[0].inspectionDate)
    }

    @Test
    fun `blank lines are ignored`() {
        val csv = """
            Kolín;01.02.2026

            Praha;02.02.2026

        """.trimIndent()

        val result = parse(csv)

        assertEquals(2, result.size)
        assertEquals(listOf("Kolín", "Praha"), result.map { it.name })
    }

    @Test
    fun `lines with fewer than two columns are ignored`() {
        val csv = """
            Kolín
            Praha;02.02.2026
        """.trimIndent()

        val result = parse(csv)

        assertEquals(1, result.size)
        assertEquals("Praha", result[0].name)
    }

    @Test
    fun `lines with empty name or empty date are skipped`() {
        val csv = """
            ;01.02.2026
            Praha;
            Kolín;03.02.2026
        """.trimIndent()

        val result = parse(csv)

        assertEquals(1, result.size)
        assertEquals("Kolín", result[0].name)
    }

    @Test
    fun `invalid date keeps the station with null inspection date`() {
        val result = parse("Kolín;not-a-date")

        assertEquals(1, result.size)
        assertEquals("Kolín", result[0].name)
        assertNull(result[0].inspectionDate)
    }

    @Test
    fun `stations are deduplicated by cleaned name`() {
        val csv = """
            Kolín;01.02.2026
            Kolín;05.02.2026
        """.trimIndent()

        val result = parse(csv)

        // distinctBy keeps the first occurrence.
        assertEquals(1, result.size)
        assertEquals(LocalDate.of(2026, 2, 1), result[0].inspectionDate)
    }

    @Test
    fun `name is normalized via StationNameCleaner`() {
        val result = parse("Praha - hlavní;01.02.2026")

        assertEquals(1, result.size)
        assertEquals("Praha", result[0].name)
    }
}
