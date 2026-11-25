package app.hermesandroid.ui.theme

import android.graphics.ImageDecoder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.hermesandroid.ui.shell.decodeImage
import app.hermesandroid.ui.shell.imageCache
import java.io.File

/**
 * The user's own portrait, shown wherever the app would otherwise show its bundled art
 * (Settings > Appearance). Mirrors DisplayPrefs: Store is the truth on disk and exposes no Flow, so
 * composition reads this snapshot and AppGraph's setter writes both sides. Device-wide on purpose —
 * a portrait is not a per-dashboard notion.
 */
object Appearance {
    /** Absolute path of the stored square portrait, or null for the app's own art. */
    var portrait by mutableStateOf<String?>(null)

    /** True when the user replaced the portrait; screens use it to offer the reset. */
    val custom: Boolean get() = portrait != null

    /**
     * The stored portrait decoded for the size it shows, or null to fall back to the bundled art.
     * Decoded once per (path, size) into the app's image cache, the way Avatar caches host photos:
     * pages rebuild on every open and rows must not re-read a file each time. A missing file (a
     * restored app without the old internal storage) is not an error — it means the app's own art.
     */
    @Composable
    fun portraitImage(px: Int): ImageBitmap? {
        val path = portrait ?: return null
        return remember(path, px) {
            runCatching {
                val key = "portrait:$path:$px"
                imageCache.get(key)
                    ?: decodeImage(ImageDecoder.createSource(File(path)), px, cover = true)?.asImageBitmap()
                        ?.also { imageCache.put(key, it) }
            }.getOrNull()
        }
    }
}
