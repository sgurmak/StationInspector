package com.example.stationinspector.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.stationinspector.domain.model.PhotoType
import com.example.stationinspector.domain.repository.StationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@HiltWorker
class ExportZipWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val stationRepository: StationRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_DATE = "export_date"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dateStr = inputData.getString(KEY_DATE) ?: return@withContext Result.failure()

        try {
            setForeground(createForegroundInfo())

            // Беремо ВСІ фотографії, щоб експорт можна було повторювати нескінченно
            val allPhotos = stationRepository.getAllPhotos().firstOrNull() ?: emptyList()

            val targetDate = try {
                LocalDate.parse(dateStr)
            } catch (e: Exception) {
                return@withContext Result.failure()
            }

            val targetDateStr = targetDate.toString()
            val photosToExport = allPhotos.filter { photo ->
                photo.assignedDate == targetDateStr
            }

            if (photosToExport.isEmpty()) {
                return@withContext Result.success()
            }

            // Тимчасова папка для формування структури
            val cacheDir = File(context.cacheDir, "export_stage_${System.currentTimeMillis()}")
            if (cacheDir.exists()) cacheDir.deleteRecursively()
            cacheDir.mkdirs()

            // Словник для перекладу на чеську
            val zoneMap = mapOf(
                "ENTRANCE" to "Nádraží",
                "STATION" to "Nádraží",
                "PLATFORM" to "Čekárna",
                "WAITING_ROOM" to "Čekárna",
                "RESTROOM" to "WC",
                "WC" to "WC",
                "Вокзал" to "Nádraží",
                "Зона очікування" to "Čekárna"
            )

            val indexMap = mutableMapOf<String, Int>()
            val photosToUpdate = mutableListOf<com.example.stationinspector.domain.model.Photo>()

            // Формуємо дату звіту (dd.MM)
            val ddMM = targetDate.format(DateTimeFormatter.ofPattern("dd.MM"))
            val rootFolderName = "KPI_$ddMM"

            for (photo in photosToExport) {
                val station = stationRepository.getStationById(photo.stationId).firstOrNull() ?: continue
                val stationName = station.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")

                // Рівень 2
                val isDefect = photo.type == PhotoType.INTERNAL_DEFECT
                val level2 = if (isDefect) "Závady" else "Fotky_ČD"

                // Рівень 3 (Чеська назва)
                val mappedZoneName = zoneMap[photo.zone.name] ?: photo.zone.name

                // Створюємо шлях (додано $stationName в ієрархію)
                val targetDirPath = "$level2/$stationName/$mappedZoneName"
                val destDir = File(cacheDir, targetDirPath)
                if (!destDir.exists()) destDir.mkdirs()

                // Рівень 4: Лічильник для перейменування файлу
                val currentIndex = indexMap.getOrDefault(targetDirPath, 0) + 1
                indexMap[targetDirPath] = currentIndex

                val sourceFile = File(photo.localPath)
                if (sourceFile.exists()) {
                    val destFileName = "${stationName}_${currentIndex}.jpg"
                    val destFile = File(destDir, destFileName)
                    sourceFile.copyTo(destFile, overwrite = true)
                    photosToUpdate.add(photo.copy(exported = true))
                }
            }

            if (photosToUpdate.isEmpty()) {
                cacheDir.deleteRecursively()
                return@withContext Result.success()
            }

            // Пакуємо тимчасову папку в ZIP (тільки папку корневу)
            val exportsDir = File(context.cacheDir, "exports")
            if (!exportsDir.exists()) exportsDir.mkdirs()

            // Очищення старих сесій експорту (zero storage waste)
            exportsDir.listFiles()?.forEach { sessionDir ->
                if (sessionDir.isDirectory && sessionDir.name.startsWith("session_")) {
                    try {
                        sessionDir.deleteRecursively()
                    } catch (e: Exception) {
                        // Файл все ще може бути заблокований зовнішнім додатком, ігноруємо до наступного запуску
                    }
                }
            }

            // Унікальна папка для кожної сесії гарантує уникнення file-locks
            val sessionDir = File(exportsDir, "session_${System.currentTimeMillis()}")
            sessionDir.mkdirs()

            val tempZipFile = File(sessionDir, "KPI_$ddMM.zip")
            if (tempZipFile.exists()) tempZipFile.delete()

            val zip = ZipFile(tempZipFile)
            // Додаємо папки Závady та Fotky_ČD напряму в корінь ZIP-архіву
            cacheDir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    zip.addFolder(file)
                } else {
                    zip.addFile(file)
                }
            }

            // Оновлюємо статус в базі і чистимо тимчасову папку
            stationRepository.savePhotos(photosToUpdate)
            cacheDir.deleteRecursively()
            // Файл tempZipFile НЕ видаляємо, щоб його можна було пошерити

            // Отримуємо FileProvider URI для експортованого ZIP
            val authority = "${context.packageName}.fileprovider"
            val finalUri = androidx.core.content.FileProvider.getUriForFile(context, authority, tempZipFile)

            // Повертаємо посилання на файл для функції "Поділитися"
            return@withContext Result.success(workDataOf("zip_uri" to finalUri.toString()))

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.failure()
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