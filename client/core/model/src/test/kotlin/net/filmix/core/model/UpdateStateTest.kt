package net.filmix.core.model

import org.junit.Assert.assertEquals
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
    fun `declining a release suppresses only that release`() {
        assertFalse(update.shouldPrompt(dismissedVersionCode = 77))
        assertFalse(update.shouldPrompt(dismissedVersionCode = 99))
        // The next release must still get through, or "Позже" once would mean
        // the device never hears about an update again.
        assertTrue(update.shouldPrompt(dismissedVersionCode = 76))
    }

    @Test
    fun `a fresh install has declined nothing`() {
        assertTrue(update.shouldPrompt(dismissedVersionCode = 0))
    }

    private fun installed(code: Int) = AppVersion(
        name = "0.6.4", code = code, gitSha = "abc", gitDirty = false, debug = false,
    )

    private val withHistory = update.copy(
        changelog = listOf(
            ReleaseNote(77, "0.6.5", listOf("Значок в лаунчере")),
            ReleaseNote(83, "0.6.7", listOf("Проверка при запуске", "И ещё")),
            ReleaseNote(79, "0.6.6", listOf("Кнопка проверки")),
        ),
    )

    @Test
    fun `a device several versions behind sees every release it missed`() {
        // The case the launch check exists for: 0.6.4 offered 0.6.7 must hear
        // about 0.6.5 and 0.6.6 too, newest first.
        val shown = withHistory.changesSince(installed(75))
        assertEquals(listOf("0.6.7", "0.6.6", "0.6.5"), shown.map { it.versionName })
        assertEquals(listOf("Проверка при запуске", "И ещё"), shown.first().notes)
    }

    @Test
    fun `one version behind sees one release`() {
        assertEquals(listOf("0.6.7"), withHistory.changesSince(installed(79)).map { it.versionName })
    }

    @Test
    fun `nothing newer shows nothing`() {
        assertEquals(emptyList<ReleaseNote>(), withHistory.changesSince(installed(83)))
    }

    @Test
    fun `an old manifest without a changelog falls back to its notes`() {
        // A new client can be pointed at a manifest published before the
        // changelog existed; it must still say something rather than nothing.
        val old = update.copy(notes = "Одна строка", changelog = emptyList())
        val shown = old.changesSince(installed(75))
        assertEquals(1, shown.size)
        assertEquals(listOf("Одна строка"), shown.single().notes)
        assertEquals("0.6.5", shown.single().versionName)
    }

    @Test
    fun `an empty manifest shows nothing rather than a blank entry`() {
        assertEquals(
            emptyList<ReleaseNote>(),
            update.copy(notes = "", changelog = emptyList()).changesSince(installed(75)),
        )
    }

    @Test
    fun `a download's progress ticks stay one stage`() {
        // The regression this pins: keying focus restoration on the state
        // itself would re-claim the cursor on every percent, yanking it back
        // twenty times while the bar fills.
        assertEquals(
            UpdateState.Downloading(update, 1).stage,
            UpdateState.Downloading(update, 99).stage,
        )
    }

    @Test
    fun `each set of controls is its own stage`() {
        assertEquals(UpdateStage.Check, UpdateState.Idle.stage)
        assertEquals(UpdateStage.Check, UpdateState.Checking.stage)
        assertEquals(UpdateStage.Check, UpdateState.UpToDate.stage)
        assertEquals(UpdateStage.Available, UpdateState.Available(update).stage)
        assertEquals(UpdateStage.Downloading, UpdateState.Downloading(update, 40).stage)
        assertEquals(UpdateStage.Ready, UpdateState.ReadyToInstall(update).stage)
        assertEquals(UpdateStage.Failed, UpdateState.Failed("boom").stage)
    }

    @Test
    fun `only a download has nowhere to put the cursor`() {
        assertFalse(UpdateStage.Downloading.hasAction)
        assertTrue(UpdateStage.Check.hasAction)
        assertTrue(UpdateStage.Available.hasAction)
        assertTrue(UpdateStage.Ready.hasAction)
        assertTrue(UpdateStage.Failed.hasAction)
    }

    @Test
    fun `states with their own buttons do not add another`() {
        assertFalse(UpdateState.Available(update).offersCheck)
        assertFalse(UpdateState.Downloading(update, 40).offersCheck)
        assertFalse(UpdateState.ReadyToInstall(update).offersCheck)
        assertFalse(UpdateState.Failed("boom").offersCheck)
    }
}
