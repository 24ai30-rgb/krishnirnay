package com.krishinirnay.llm

import android.content.Context
import android.os.StatFs
import com.krishinirnay.core.llm.local.ModelDownloadState
import com.krishinirnay.core.llm.local.OnDeviceModelManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [OnDeviceModelManager] is what stands between a farmer tapping "Download"
 * and a 500MB+ file landing on their phone — these tests prove the two
 * honesty guarantees that matter most: it never starts a download it
 * already knows won't fit, and it never treats a truncated/interrupted
 * download as a real, loadable model.
 */
class OnDeviceModelManagerTest {

    private lateinit var tempDir: File
    private lateinit var context: Context

    @Before
    fun setUp() {
        tempDir = kotlin.io.path.createTempDirectory("on_device_model_test").toFile()
        context = mockk()
        every { context.filesDir } returns tempDir
        // StatFs is an Android SDK stub in plain JVM unit tests (see
        // app/build.gradle.kts's isReturnDefaultValues) — every real call
        // returns 0, which would make every download fail the storage
        // check. Mocked here to a large default; individual tests override
        // it to exercise the "not enough storage" path specifically.
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBytes } returns Long.MAX_VALUE / 2
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        unmockkConstructor(StatFs::class)
    }

    private fun fakeClient(response: Response): OkHttpClient {
        val call = mockk<Call>()
        every { call.execute() } returns response
        val client = mockk<OkHttpClient>()
        every { client.newCall(any<Request>()) } returns call
        return client
    }

    private fun responseWithBody(bytes: ByteArray): Response {
        return Response.Builder()
            .request(Request.Builder().url("http://127.0.0.1/model").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(bytes.toResponseBody(null))
            .build()
    }

    /** A body that CLAIMS [declaredContentLength] via contentLength() but only actually streams [actualBytes] — simulates a connection that drops mid-transfer. */
    private fun responseWithTruncatedBody(actualBytes: ByteArray, declaredContentLength: Long): Response {
        val body = object : ResponseBody() {
            override fun contentType() = null
            override fun contentLength() = declaredContentLength
            override fun source(): BufferedSource = Buffer().write(actualBytes)
        }
        return Response.Builder()
            .request(Request.Builder().url("http://127.0.0.1/model").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()
    }

    @Test
    fun `starts NotDownloaded when no model file exists yet`() {
        val manager = OnDeviceModelManager(context, fakeClient(responseWithBody(ByteArray(0))))
        assertEquals(ModelDownloadState.NotDownloaded, manager.downloadState.value)
    }

    @Test
    fun `a blank download URL fails honestly without touching the network`() = runTest {
        val client = mockk<OkHttpClient>()
        val manager = OnDeviceModelManager(context, client)

        manager.download("")

        val state = manager.downloadState.value
        assertTrue(state is ModelDownloadState.Failed)
        assertTrue((state as ModelDownloadState.Failed).reason.contains("URL"))
    }

    @Test
    fun `a completed download lands at the final model file path and reports Downloaded`() = runTest {
        val bytes = ByteArray(1024) { it.toByte() }
        val manager = OnDeviceModelManager(context, fakeClient(responseWithBody(bytes)))

        manager.download("http://127.0.0.1:8000/v1/local-llm/on-device-model")

        assertEquals(ModelDownloadState.Downloaded, manager.downloadState.value)
        assertTrue(manager.modelFile.exists())
        assertEquals(bytes.size.toLong(), manager.modelFile.length())
        // No leftover partial file once the real one is in place.
        assertTrue(File(manager.modelFile.parentFile, manager.modelFile.name + ".part").exists().not())
    }

    @Test
    fun `a download that ends short of the declared size is never accepted as a real model`() = runTest {
        // Body only actually streams 10 bytes, but claims 1000 up front.
        val response = responseWithTruncatedBody(ByteArray(10), declaredContentLength = 1000)
        val manager = OnDeviceModelManager(context, fakeClient(response))

        manager.download("http://127.0.0.1:8000/v1/local-llm/on-device-model")

        val state = manager.downloadState.value
        assertTrue(state is ModelDownloadState.Failed)
        assertTrue(manager.modelFile.exists().not())
    }

    @Test
    fun `refuses to start a download that would leave the device with no free storage`() = runTest {
        // Real available space is tiny — far less than the file plus the
        // safety margin — so the download must never write a single byte.
        every { anyConstructed<StatFs>().availableBytes } returns 100L
        val bytes = ByteArray(1_000_000)
        val manager = OnDeviceModelManager(context, fakeClient(responseWithBody(bytes)))

        manager.download("http://127.0.0.1:8000/v1/local-llm/on-device-model")

        val state = manager.downloadState.value
        assertTrue(state is ModelDownloadState.Failed)
        assertTrue((state as ModelDownloadState.Failed).reason.contains("storage"))
        assertTrue(manager.modelFile.exists().not())
    }

    @Test
    fun `an HTTP failure from the server is reported, not silently ignored`() = runTest {
        val response = Response.Builder()
            .request(Request.Builder().url("http://127.0.0.1/model").build())
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .body(ByteArray(0).toResponseBody(null))
            .build()
        val manager = OnDeviceModelManager(context, fakeClient(response))

        manager.download("http://127.0.0.1:8000/v1/local-llm/on-device-model")

        val state = manager.downloadState.value
        assertTrue(state is ModelDownloadState.Failed)
        assertTrue((state as ModelDownloadState.Failed).reason.contains("404"))
    }

    @Test
    fun `deleteDownloadedModel removes the file and reports NotDownloaded again`() = runTest {
        val bytes = ByteArray(100)
        val manager = OnDeviceModelManager(context, fakeClient(responseWithBody(bytes)))
        manager.download("http://127.0.0.1:8000/v1/local-llm/on-device-model")
        assertEquals(ModelDownloadState.Downloaded, manager.downloadState.value)

        manager.deleteDownloadedModel()

        assertEquals(ModelDownloadState.NotDownloaded, manager.downloadState.value)
        assertTrue(manager.modelFile.exists().not())
    }
}
