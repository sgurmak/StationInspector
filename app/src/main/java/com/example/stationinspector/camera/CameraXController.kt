package com.example.stationinspector.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraInfo
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraXController @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "CameraXController"
    }

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: androidx.camera.core.Camera? = null

    /**
     * Initializes and binds the camera to the lifecyle. Allows continuous preview
     * without needing to restart it for each capture.
     */
    suspend fun startCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ): Unit = withContext(Dispatchers.Main) {
        val provider = getCameraProvider(context)
        cameraProvider = provider

        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
            .also {
                it.setSurfaceProvider(surfaceProvider)
            }

        imageCapture = ImageCapture.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            // Unbind use cases before rebinding
            provider.unbindAll()

            // Bind use cases to camera
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
            Log.d(TAG, "Camera bound to lifecycle successfully.")
            Unit
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
            Unit
        }
    }

    /**
     * Captures an image and returns it as a Bitmap.
     * Uses coroutines for an asynchronous, non-blocking flow.
     */
    suspend fun takePicture(): Bitmap? = suspendCancellableCoroutine { continuation ->
        val captureTemplate = imageCapture ?: run {
            Log.e(TAG, "ImageCapture use case not initialized")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        captureTemplate.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotationDegrees = image.imageInfo.rotationDegrees
                    val originalBitmap = image.toBitmap()
                    
                    val rotatedBitmap = if (rotationDegrees != 0) {
                        val matrix = android.graphics.Matrix()
                        matrix.postRotate(rotationDegrees.toFloat())
                        Bitmap.createBitmap(
                            originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
                        ).also {
                            // The rotated copy is a new bitmap; free the source to
                            // avoid leaking a full-resolution bitmap per capture.
                            originalBitmap.recycle()
                        }
                    } else {
                        originalBitmap
                    }

                    image.close()
                    Log.d(TAG, "Picture taken successfully, rotated by $rotationDegrees degrees")
                    continuation.resume(rotatedBitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    continuation.resumeWithException(exception)
                }
            }
        )
    }

    private suspend fun getCameraProvider(context: Context): ProcessCameraProvider =
        suspendCancellableCoroutine { continuation ->
            ProcessCameraProvider.getInstance(context).also { cameraProviderFuture ->
                cameraProviderFuture.addListener({
                    continuation.resume(cameraProviderFuture.get())
                }, ContextCompat.getMainExecutor(context))
            }
        }

    fun stopCamera() {
        // Unbind AND release the references so the camera fully powers down when
        // leaving the screen, and a stale unbound use case can't be reused.
        cameraProvider?.unbindAll()
        imageCapture = null
        camera = null
        cameraProvider = null
    }

    fun setZoomRatio(zoomRatio: Float) {
        camera?.cameraControl?.setZoomRatio(zoomRatio)
    }

    fun getCameraInfo(): CameraInfo? {
        return camera?.cameraInfo
    }

    fun setFlashMode(flashMode: Int) {
        imageCapture?.flashMode = flashMode
    }
}