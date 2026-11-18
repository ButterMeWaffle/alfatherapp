package app.hermesandroid.sessions

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** Untitled chats are named after their first message: Hermes' prefixes give way to the words or a name. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = app.hermesandroid.HermesApp::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class UntitledRowTest {
    private fun titleOf(value: String, key: String = "preview") = ChatsState.rowOf(buildJsonObject { put("id", "s1"); put(key, value) })!!.title

    @Test fun imagePathIsDropped() = assertEquals("What plant is this?", titleOf("[Image attached at: /home/a/.hermes/img.png] What plant is this?"))
    @Test fun imageAloneIsNamed() = assertEquals("Image attached", titleOf("[Image attached at: /home/a/.hermes/img.png]"))
    @Test fun cutTitleIsCleaned() = assertEquals("Image attached", titleOf("[Image attached at: /home/a/.hermes/imag…", key = "title"))
    @Test fun phoneRulesAreNamed() = assertEquals("With phone control", titleOf("<system-reminder>\nrules</system-reminder> open WhatsApp"))
    @Test fun plainPreviewStays() = assertEquals("Hello", titleOf("Hello"))
}
