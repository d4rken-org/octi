package eu.darken.octi.common.review

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.serialization.SerializationModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class ReviewSettingsTest : BaseTest() {

    // One test method on purpose: ReviewSettings owns its DataStore, and DataStore forbids two
    // active instances on the same file — a second ReviewSettings in this process would crash,
    // not exercise anything real.
    @Test
    fun `the review timestamps round-trip through the DataStore`() = runTest {
        // Real time on purpose: the real DataStore does its I/O off the test scheduler.
        withContext(Dispatchers.IO) {
            // Real DataStore + the production Json, no mocks: this catches an encoding mismatch
            // between what the settings write and what they read back.
            val settings = ReviewSettings(
                context = ApplicationProvider.getApplicationContext<Context>(),
                json = SerializationModule().json(),
            )

            // A fresh install has never dismissed and never reviewed — both gates read "no".
            settings.lastDismissed.value() shouldBe null
            settings.reviewedAt.value() shouldBe null

            val dismissedAt = Instant.parse("2024-01-01T00:00:00Z")
            settings.lastDismissed.value(dismissedAt)
            settings.lastDismissed.value() shouldBe dismissedAt

            val reviewedAt = Instant.parse("2024-06-15T12:34:56Z")
            settings.reviewedAt.value(reviewedAt)
            settings.reviewedAt.value() shouldBe reviewedAt

            // The two keys are independent: writing one must not disturb the other.
            settings.lastDismissed.value() shouldBe dismissedAt

            // Clearing goes back to the default instead of persisting a literal "null" payload.
            settings.lastDismissed.value(null)
            settings.lastDismissed.value() shouldBe null

            // Pins the loud-failure behaviour: these values are created WITHOUT
            // onErrorFallbackToDefault, so unreadable data surfaces instead of silently reading
            // as "never reviewed" (which would re-ask a user who already left a review).
            settings.dataStore.edit { prefs ->
                prefs[stringPreferencesKey("review.reviewedAt")] = "not-an-instant"
            }
            shouldThrow<SerializationException> { settings.reviewedAt.value() }
        }
    }
}
