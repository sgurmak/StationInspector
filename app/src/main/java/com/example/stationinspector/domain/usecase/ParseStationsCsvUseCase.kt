package com.example.stationinspector.domain.usecase

import com.example.stationinspector.domain.model.Station
import com.example.stationinspector.domain.model.StationStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * Pure parser that turns raw CSV text into a deduplicated list of [Station]s.
 *
 * Format per line: `name<sep>date` where `<sep>` is `;` (preferred) or `,`.
 * Dates use `dd.MM.yyyy`; unparseable dates become `null` (kept, not dropped,
 * matching the historic import behavior). Names are normalized through
 * [StationNameCleaner] and the result is deduplicated by cleaned name.
 *
 * No Android or I/O dependencies — fully unit-testable.
 */
class ParseStationsCsvUseCase @Inject constructor() {

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** Convenience overload for in-memory text (used by unit tests). */
    operator fun invoke(csvContent: String): List<Station> = invoke(csvContent.lineSequence())

    /**
     * Streaming entry point: parses a lazy [Sequence] of lines so the caller can
     * feed a file line-by-line without loading it entirely into memory. Only the
     * (small) parsed station list is materialized for deduplication.
     */
    operator fun invoke(lines: Sequence<String>): List<Station> =
        lines.mapNotNull { parseLine(it) }
            .toList()
            // Deduplicate: one entry per physical (cleaned) station name.
            .distinctBy { it.name }

    private fun parseLine(rawLine: String): Station? {
        val line = rawLine.trim()
        if (line.isBlank()) return null

        val parts = if (line.contains(';')) line.split(';') else line.split(',')
        if (parts.size < 2) return null

        val name = parts[0].trim()
        val dateStr = parts[1].trim()
        if (name.isBlank() || dateStr.isBlank()) return null

        val date = try {
            LocalDate.parse(dateStr, dateFormatter)
        } catch (e: DateTimeParseException) {
            null
        }

        return Station(
            id = 0,
            name = StationNameCleaner.clean(name),
            address = "",
            inspectionDate = date,
            status = StationStatus.PENDING
        )
    }
}
