package net.filmix.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateStateTest {

    private val update = AppUpdate(
        versionCode = 77,
        versionName = "0.6.5",
        commit = "93cb8eb",
        apkUrl = "https://example.invalid/app.apk",
        sizeBytes = 2_987_253,
        sha256 = "aa78",
        notes = "",
    )

    @Test
    fun `check button survives a check that found nothing`() {
        // The regression this pins: UpToDate is terminal — no transition ever
        // goes back to Idle — so dropping the button there stranded it until
        // the process restarted.
        assertTrue(UpdateState.UpToDate.offersCheck)
    }

    @Test
    fun `check button spans idle and checking`() {
        assertTrue(UpdateState.Idle.offersCheck)
        assertTrue(UpdateState.Checking.offersCheck)
    }

    @Test
    fun `states with their own buttons do not add another`() {
        assertFalse(UpdateState.Available(update).offersCheck)
        assertFalse(UpdateState.Downloading(update, 40).offersCheck)
        assertFalse(UpdateState.ReadyToInstall(update).offersCheck)
        assertFalse(UpdateState.Failed("boom").offersCheck)
    }
}
