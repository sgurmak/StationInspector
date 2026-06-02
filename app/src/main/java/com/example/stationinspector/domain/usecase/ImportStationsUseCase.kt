package com.example.stationinspector.domain.usecase

import android.util.Log
import com.example.stationinspector.domain.repository.StationRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject

/**
 * Reads a CSV [InputStream], parses it with [ParseStationsCsvUseCase], persists
 * each unique station, and seeds missing coordinates.
 *
 * The ViewModel only deals with an already-opened [InputStream]; opening it from
 * a content `Uri` stays in the UI layer so this use case (and the ViewModel)
 * never touches Android `Context`.
 *
 * @return [Result] with the number of stations imported, or the failure.
 */
class ImportStationsUseCase @Inject constructor(
    private val parseStationsCsv: ParseStationsCsvUseCase,
    private val stationRepository: StationRepository
) {

    suspend operator fun invoke(
        inputStream: InputStream,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            // Stream line-by-line instead of readBytes() so an oversized/hostile
            // file (the picker uses GetContent("*/*")) cannot OOM the process.
            val stations = inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                parseStationsCsv(lines)
            }

            stations.forEach { station ->
                try {
                    stationRepository.saveStation(station)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save station '${station.name}' during CSV import", e)
                }
            }

            stationRepository.seedCoordinatesIfMissing()
            stations.size
        }
    }

    private companion object {
        const val TAG = "ImportStationsUseCase"
    }
}
