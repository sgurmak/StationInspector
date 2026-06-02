package com.example.stationinspector.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.stationinspector.domain.model.Photo
import com.example.stationinspector.domain.model.PhotoType
import com.example.stationinspector.domain.model.PhotoZone
import com.example.stationinspector.domain.repository.StationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@HiltWorker
class ExportZipWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val stationRepository: StationRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_DATE = "export_date"
        private const val TAG = "ExportZipWorker"

        /** Czech folder names for the exported zones. */
        private val ZONE_FOLDER = mapOf(
            PhotoZone.ENTRANCE to "Nádraží",
            PhotoZone.PLATFORM to "Čekárna",
            PhotoZone.RESTROOM to "WC"
        )
    }

    /**
     * Makes [raw] safe to use as a single path segment: strips path separators
     * and reserved characters, collapses to "_" if empty, and rejects "."/".."
     * so a crafted station name can't escape the staging directory.
     */
    private fun sanitizeSegment(raw: String): String {
        val cleaned = raw
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .trim('.')
            .trim()
        return cleaned.ifBlank { "_" }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dateStr = inputData.getString(KEY_DATE) ?: return@withContext Result.failure()
        val targetDate = try {
            LocalDate.parse(dateStr)
        } catch (e: Exception) {
            Log.w(TAG, "Invalid export date '$dateStr'", e)
            return@withContext Result.failure()
        }

        // Staging dir is created up-front so the finally block can always remove
        // it — previously a mid-export exception leaked it in cacheDir forever.
        val stagingDir = File(context.cacheDir, "export_stage_${System.currentTimeMillis()}")
        try {
            setForeground(createForegroundInfo())

            val targetDateStr = targetDate.toString()
            // Export every matching photo so the export can be repeated freely.
            val photosToExport = stationRepository.getAllPhotos().firstOrNull()
                ?.filter { it.assignedDate == targetDateStr }
                ?: emptyList()
            if (photosToExport.isEmpty()) return@withContext Result.success()

            if (stagingDir.exists()) stagingDir.deleteRecursively()
            stagingDir.mkdirs()

            // Resolve each station exactly once instead of querying per photo.
            val stationsById = photosToExport.map { it.stationId }.distinct()
                .associateWith { id -> stationRepository.getStationById(id).firstOrNull() }

            val indexMap = mutableMapOf<String, Int>()
            val photosToUpdate = mutableListOf<Photo>()
            val ddMM = targetDate.format(DateTimeFormatter.ofPattern("dd.MM"))

            for (photo in photosToExport) {
                val station = stationsById[photo.stationId] ?: continue
                val stationName = sanitizeSegment(station.name)
                val level2 = if (photo.type == PhotoType.INTERNAL_DEFECT) "Závady" else "Fotky_ČD"
                val zoneFolder = sanitizeSegment(ZONE_FOLDER[photo.zone] ?: photo.zone.name)

                val targetDirPath = "$level2/$stationName/$zoneFolder"
                val destDir = File(stagingDir, targetDirPath).also { it.mkdirs() }

                val currentIndex = indexMap.getOrDefault(targetDirPath, 0) + 1
                indexMap[targetDirPath] = currentIndex

                val sourceFile = File(photo.localPath)
                if (sourceFile.exists()) {
                    sourceFile.copyTo(File(destDir, "${stationName}_$currentIndex.jpg"), overwrite = true)
                    photosToUpdate.add(photo.copy(exported = true))
                }
            }

            if (photosToUpdate.isEmpty()) return@withContext Result.success()

            val exportsDir = File(context.cacheDir, "exports").also { it.mkdirs() }
            // Drop previous export sessions (zero storage waste). A session may
            // still be locked by an external app — ignore and retry next run.
            exportsDir.listFiles()?.forEach { session ->
                if (session.isDirectory && session.name.startsWith("session_")) {
                    runCatching { session.deleteRecursively() }
                }
            }

            // A fresh session dir per export avoids file-locks on the shared ZIP.
            val sessionDir = File(exportsDir, "session_${System.currentTimeMillis()}").also { it.mkdirs() }
            val zipFile = File(sessionDir, "KPI_$ddMM.zip")
            if (zipFile.exists()) zipFile.delete()

            ZipFile(zipFile).use { zip ->
                stagingDir.listFiles()?.forEach { file ->
                    if (file.isDirectory) zip.addFolder(file) else zip.addFile(file)
                }
            }

            // Mark exported only after the ZIP is finalized.
            stationRepository.savePhotos(photosToUpdate)

            val authority = "${context.packageName}.fileprovider"
            val finalUri = FileProvider.getUriForFile(context, authority, zipFile)
            Result.success(workDataOf("zip_uri" to finalUri.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            Result.failure()
        } finally {
            // Always remove the staging copy — on success, failure or cancellation.
            stagingDir.deleteRecursively()
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val channelId = "export_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Export", NotificationManager.IMPORTANCE_LOW)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Генерація звіту")
            .setContentText("Створення ZIP-архіву...")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(1, notification)
        }
    }
}