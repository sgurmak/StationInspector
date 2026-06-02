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

    operator fun invoke(csvContent: String): List<Station> {
        val rawStations = csvContent.lineSequence().mapNotNull { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@mapNotNull null

            val parts = if (line.contains(';')) line.split(';') else line.split(',')
            if (parts.size < 2) return@mapNotNull null

            val name = parts[0].trim()
            val dateStr = parts[1].trim()
            if (name.isBlank() || dateStr.isBlank()) return@mapNotNull null

            val date = try {
                LocalDate.parse(dateStr, dateFormatter)
            } catch (e: DateTimeParseException) {
                null
            }

            Station(
                id = 0,
                name = StationNameCleaner.clean(name),
                address = "",
                inspectionDate = date,
                status = StationStatus.PENDING
            )
        }.toList()

        // Deduplicate: one entry per physical (cleaned) station name.
        return rawStations.distinctBy { it.name }
    }
}
