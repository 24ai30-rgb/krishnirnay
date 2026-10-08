package com.krishinirnay.core.llm.local

import android.content.Context
import android.os.StatFs
import android.util.Log
import com.krishinirnay.core.network.di.ModelDownloadClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "KRISHI_ONDEVICE_MODEL"

/** File name the app looks for/stores under the app's own private storage — never public/shared storage. */
private const val MODEL_FILE_NAME = "gemma3_1b_int4.task"

/**
 * Extra headroom required beyond the download's own reported size, so the
 * download never leaves the device with zero free space (which can make the
 * OS kill the app or corrupt other app data) — not a guess, a fixed 20%
 * safety margin on top of the real Content-Length the server reports.
 */
private const val STORAGE_SAFETY_MARGIN = 1.2

sealed interface ModelDownloadState {
    data object NotDownloaded : ModelDownloadState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : ModelDownloadState
    data object Downloaded : ModelDownloadState
    data class Failed(val reason: String) : ModelDownloadState
}

/**
 * Manages the on-device LLM model file — download, storage-space check, and
 * deletion. Deliberately separate from [MediaPipeOnDeviceLlmProvider] (which
 * only ever *reads* this file): downloading a large model is a device
 * storage/UX decision the farmer must explicitly trigger (a Settings
 * button), never something that happens silently on app start.
 *
 * The model is never bundled in the APK (Google's own guidance: a
 * quantized 1B-parameter model is too large for that) and never downloaded
 * from a third party directly — like every other provider-backed feature in
 * this app (Weather/Market/Disease), it comes from this app's own server,
 * see [com.krishinirnay.BuildConfig.SERVER_BASE_URL].
 */
@Singleton
class OnDeviceModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ModelDownloadClient private val httpClient: OkHttpClient,
) {
    private val modelDir: File get() = File(context.filesDir, "models")

    /** The real, loadable model file — only valid to hand to [MediaPipeOnDeviceLlmProvider] when [downloadState] is [ModelDownloadState.Downloaded]. */
    val modelFile: File get() = File(modelDir, MODEL_FILE_NAME)

    private val _downloadState = MutableStateFlow<ModelDownloadState>(
        if (modelFile.exists() && modelFile.length() > 0) ModelDownloadState.Downloaded else ModelDownloadState.NotDownloaded,
    )
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    /** Free bytes actually available in this app's private storage right now — never guessed. */
    fun availableStorageBytes(): Long = StatFs(context.filesDir.path).let { it.availableBytes }

    /**
     * Downloads the model from [downloadUrl] (this app's own server — see
     * class doc). Checks real free storage against the server's declared
     * Content-Length (with [STORAGE_SAFETY_MARGIN]) before writing a single
     * byte, so a doomed download never starts. A file is only ever moved to
     * its final [modelFile] path once every declared byte has been written —
     * an interrupted/failed download leaves no partial file a stale check
     * could mistake for a real model.
     */
    suspend fun download(downloadUrl: String) {
        if (downloadUrl.isBlank()) {
            _downloadState.value = ModelDownloadState.Failed("No on-device model download URL is configured.")
            return
        }

        _downloadState.value = ModelDownloadState.Downloading(0, 0)
        modelDir.mkdirs()
        val tempFile = File(modelDir, "$MODEL_FILE_NAME.part")

        try {
            val request = Request.Builder().url(downloadUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Empty download response body.")
                val totalBytes = body.contentLength()
                if (totalBytes <= 0) {
                    throw IOException("Server did not report a download size — refusing to download blind.")
                }

                val required = (totalBytes * STORAGE_SAFETY_MARGIN).toLong()
                val available = availableStorageBytes()
                if (available < required) {
                    throw IOException(
                        "Not enough storage: need ~${required.toMb()}MB free, only ${available.toMb()}MB available.",
                    )
                }

                var downloaded = 0L
                _downloadState.value = ModelDownloadState.Downloading(0, totalBytes)
                tempFile.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                            downloaded += read
                            _downloadState.value = ModelDownloadState.Downloading(downloaded, totalBytes)
                        }
                    }
                }

                if (downloaded != totalBytes) {
                    throw IOException("Download ended early: got ${downloaded.toMb()}MB of ${totalBytes.toMb()}MB.")
                }
            }

            modelFile.delete() // in case a previous (now-untrusted) copy exists
            if (!tempFile.renameTo(modelFile)) {
                throw IOException("Could not save the downloaded model to its final location.")
            }
            Log.i(TAG, "On-device model downloaded successfully: ${modelFile.length().toMb()}MB")
            _downloadState.value = ModelDownloadState.Downloaded
        } catch (e: Exception) {
            tempFile.delete()
            Log.w(TAG, "On-device model download failed: ${e.message}")
            _downloadState.value = ModelDownloadState.Failed(e.message ?: "Download failed.")
        }
    }

    /** Removes a downloaded model (e.g. the farmer wants the storage back) — [MediaPipeOnDeviceLlmProvider] must re-check before its next use. */
    fun deleteDownloadedModel() {
        modelFile.delete()
        _downloadState.value = ModelDownloadState.NotDownloaded
    }

    private fun Long.toMb(): Long = this / 1_000_000
}
