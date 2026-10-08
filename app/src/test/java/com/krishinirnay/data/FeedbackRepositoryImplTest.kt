package com.krishinirnay.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.krishinirnay.core.data.local.FeedbackRepositoryImpl
import com.krishinirnay.core.data.local.FeedbackStore
import com.krishinirnay.core.data.model.FeedbackAction
import com.krishinirnay.core.data.model.FeedbackEntry
import com.krishinirnay.core.data.model.FeedbackResult
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun entry(action: FeedbackAction, result: FeedbackResult? = null, notes: String? = null) = FeedbackEntry(
    id = "id-1", timestamp = Instant.EPOCH, crop = "Cotton", cropStage = "Flowering",
    recommendation = "IrrigateWaterHigh", actionTaken = action, result = result, notes = notes,
)

/**
 * Real DataStore backed by a temp file — feedback must survive a restart
 * with zero network. Matches [LiveWeatherRepositoryImplTest]'s established
 * pattern: the same [FeedbackStore] instance (a `@Singleton` in production,
 * never recreated mid-process) is reused across two `FeedbackRepositoryImpl`
 * instances to prove a fresh repository correctly reads what an earlier one
 * persisted — constructing a genuinely new on-disk DataStore for the second
 * instance would cross a real (not virtual-time) I/O dispatch that a plain
 * `UnconfinedTestDispatcher` can't deterministically resolve before the test
 * body's very next line runs.
 *
 * Note: `FeedbackRepositoryImpl.record()` explicitly awaits its initial load
 * before mutating state, specifically so a record() call made immediately
 * after construction can never be clobbered by that load completing late.
 * A dedicated test reproducing that exact race (mocking FeedbackStore with a
 * delayed load under StandardTestDispatcher) was attempted but abandoned:
 * this environment's MockK/ByteBuddy version doesn't support this JVM
 * (Java 25 vs Byte Buddy's Java 23 ceiling — confirmed via captured
 * system-err, not a bug in this code), so mocking a concrete class inside a
 * coroutine delay produced unreliable results unrelated to the logic under
 * test. The fix is retained; verification here is via code review.
 */
class FeedbackRepositoryImplTest {

    private fun newStore(tempDir: File) = FeedbackStore(
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            produceFile = { File(tempDir, "test_feedback.preferences_pb") },
        ),
    )

    private fun createTempDir(): File = File.createTempFile("feedback_test", "").let {
        it.delete()
        it.mkdirs()
        it
    }

    @Test
    fun `starts empty before anything has been recorded`() = runTest(UnconfinedTestDispatcher()) {
        val repo = FeedbackRepositoryImpl(backgroundScope, newStore(createTempDir()))
        assertEquals(emptyList<FeedbackEntry>(), repo.entries.value)
    }

    @Test
    fun `recording YES with a result persists and survives repository recreation`() = runTest(UnconfinedTestDispatcher()) {
        val store = newStore(createTempDir())
        val repo = FeedbackRepositoryImpl(backgroundScope, store)

        repo.record(entry(FeedbackAction.YES, FeedbackResult.CROP_IMPROVED))

        assertEquals(1, repo.entries.value.size)

        val restarted = FeedbackRepositoryImpl(backgroundScope, store)
        assertEquals(listOf(entry(FeedbackAction.YES, FeedbackResult.CROP_IMPROVED)), restarted.entries.value)
    }

    @Test
    fun `recording NO with no result field never invents one`() = runTest(UnconfinedTestDispatcher()) {
        val repo = FeedbackRepositoryImpl(backgroundScope, newStore(createTempDir()))
        repo.record(entry(FeedbackAction.NO))

        assertEquals(FeedbackAction.NO, repo.entries.value.single().actionTaken)
        assertEquals(null, repo.entries.value.single().result)
    }

    @Test
    fun `recording PARTIALLY with optional notes roundtrips the notes`() = runTest(UnconfinedTestDispatcher()) {
        val store = newStore(createTempDir())
        val repo = FeedbackRepositoryImpl(backgroundScope, store)
        repo.record(entry(FeedbackAction.PARTIALLY, FeedbackResult.YIELD_REDUCED, notes = "Rain came late"))

        val restarted = FeedbackRepositoryImpl(backgroundScope, store)
        assertEquals("Rain came late", restarted.entries.value.single().notes)
    }

    @Test
    fun `multiple recordings accumulate rather than overwrite`() = runTest(UnconfinedTestDispatcher()) {
        val repo = FeedbackRepositoryImpl(backgroundScope, newStore(createTempDir()))
        repo.record(entry(FeedbackAction.YES).copy(id = "id-1"))
        repo.record(entry(FeedbackAction.NO).copy(id = "id-2"))

        assertEquals(2, repo.entries.value.size)
        assertTrue(repo.entries.value.map { it.id }.containsAll(listOf("id-1", "id-2")))
    }
}
