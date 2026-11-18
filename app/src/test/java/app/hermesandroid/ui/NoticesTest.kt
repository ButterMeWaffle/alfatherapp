package app.hermesandroid.ui

import app.hermesandroid.Notices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoticesTest {
    private val sent = mutableListOf<String>()
    private val notices = Notices(CoroutineScope(Dispatchers.Unconfined))
    private fun held(name: String): suspend () -> Unit = { sent += name }

    @Test fun timeoutSendsOnce() {
        notices.notify("Hecha", undo = {}, commit = held("a"))
        val n = notices.current!!
        notices.settle(n); notices.settle(n)
        assertEquals(listOf("a"), sent)
        assertNull(notices.current)
    }

    @Test fun undoNeverSends() {
        var undone = false
        notices.notify("Quitada", undo = { undone = true }, commit = held("a"))
        val n = notices.current!!
        notices.undo(n); notices.settle(n)
        assertEquals(true, undone)
        assertEquals(emptyList<String>(), sent)
    }

    @Test fun replacedSendsTheOneBefore() {
        notices.notify("Primera", undo = {}, commit = held("a"))
        val first = notices.current!!
        notices.notify("Segunda", undo = {}, commit = held("b"))
        notices.settle(first) // its timer firing late changes nothing
        assertEquals(listOf("a"), sent)
        assertEquals("Segunda", notices.current?.text)
    }
}
