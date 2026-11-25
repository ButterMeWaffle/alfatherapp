package app.hermesandroid.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.hermesandroid.AppGraph
import app.hermesandroid.R
import app.hermesandroid.ui.components.SettingsButton
import app.hermesandroid.ui.icons.AF
import app.hermesandroid.ui.shell.Avatar
import app.hermesandroid.ui.theme.Appearance
import app.hermesandroid.ui.theme.C
import app.hermesandroid.ui.theme.CType
import app.hermesandroid.ui.shell.decodeImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Settings > Appearance: the user's own portrait, shown in the chat, the sidebar, the lock screen and
 * the assistant instead of AllFather's art.
 *
 * The picked picture is copied into the app's own storage as a 256 px square PNG (the app never keeps
 * a content Uri — every other image path here copies bytes), so it survives the picker's temporary
 * grant, the app's restart, and a source photo that is later edited, moved or deleted.
 */
@Composable
fun AppearancePage(graph: AppGraph) {
    val c = C.colors
    val context = LocalContext.current
    val scope = graph.scope
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            failed = false
            scope.launch {
                val path = withContext(Dispatchers.IO) { storePortrait(context, uri) }
                if (path != null) graph.setPortrait(path) else failed = true
                busy = false
            }
        }
    }
    Card(stringResource(R.string.appearancepage_portrait), AF.Image) {
        Help(stringResource(R.string.appearancepage_portrait_help))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar("default", 64.dp)
            Text(
                stringResource(if (Appearance.custom) R.string.appearancepage_using_custom else R.string.appearancepage_using_default),
                style = CType.subheadline, color = c.text,
            )
        }
        Help(stringResource(R.string.appearancepage_square_help))
        when {
            busy -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), color = c.accent, strokeWidth = 2.dp)
                Help(stringResource(R.string.appearancepage_working))
            }
            else -> {
                SettingsButton(
                    stringResource(if (Appearance.custom) R.string.appearancepage_replace else R.string.appearancepage_pick),
                    AF.Image, prominent = !Appearance.custom,
                ) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                if (Appearance.custom) SettingsButton(stringResource(R.string.appearancepage_reset), AF.Close) {
                    scope.launch {
                        withContext(Dispatchers.IO) { deletePortrait(context) }
                        graph.setPortrait("")
                    }
                }
            }
        }
        if (failed) Help(stringResource(R.string.appearancepage_failed))
    }
}

/**
 * The picked picture, centre-cropped to a 256 px square in the app's private files, or null when it
 * cannot be read. Heavy decoding stays off the caller (CONTRIBUTING), same shape as a bot avatar.
 */
private fun storePortrait(context: Context, uri: Uri): String? = runCatching {
    val src = decodeImage(ImageDecoder.createSource(context.contentResolver, uri), 256, cover = true, software = true) ?: return@runCatching null
    val edge = min(src.width, src.height)
    val square = Bitmap.createBitmap(src, (src.width - edge) / 2, (src.height - edge) / 2, edge, edge)
    val scaled = Bitmap.createScaledBitmap(square, 256, 256, true)
    val out = File(File(context.filesDir, "appearance").apply { mkdirs() }, "portrait.png")
    out.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
    out.absolutePath
}.getOrNull()

/** The stored copy goes with the setting: a reset leaves no orphan file behind. */
private fun deletePortrait(context: Context) { runCatching { File(context.filesDir, "appearance/portrait.png").delete() } }
