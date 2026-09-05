package com.krishinirnay.core.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.krishinirnay.core.common.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Loads `assets/models/model1_irrigation_rf.onnx` once, lazily, off the
 * main thread. No real trained model file has been provided yet, so
 * session creation is expected to fail until one is added — [run]
 * returns null in that case rather than crashing, and
 * [com.krishinirnay.core.decision.DecisionEngine] already treats a null
 * model output as "fall back to the rule-based threshold," so the app
 * stays fully functional either way.
 */
@Singleton
class OnnxModelRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val loadMutex = Mutex()
    private var session: OrtSession? = null
    private var loadAttempted = false

    private suspend fun getSession(): OrtSession? = loadMutex.withLock {
        if (!loadAttempted) {
            loadAttempted = true
            session = runCatching {
                val modelBytes = context.assets.open(MODEL_ASSET_PATH).use { it.readBytes() }
                environment.createSession(modelBytes)
            }.getOrNull()
        }
        session
    }

    /** Returns the raw output tensor's first element as a [FloatArray], or null if the model isn't loaded or inference fails. */
    suspend fun run(input: FloatArray): FloatArray? = withContext(dispatcherProvider.default) {
        val activeSession = getSession() ?: return@withContext null
        runCatching {
            val inputName = activeSession.inputNames.first()
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), longArrayOf(1, input.size.toLong())).use { tensor ->
                activeSession.run(mapOf(inputName to tensor)).use { result ->
                    when (val output = result[0].value) {
                        is Array<*> -> output.firstOrNull() as? FloatArray
                        is FloatArray -> output
                        else -> null
                    }
                }
            }
        }.getOrNull()
    }

    private companion object {
        const val MODEL_ASSET_PATH = "models/model1_irrigation_rf.onnx"
    }
}
