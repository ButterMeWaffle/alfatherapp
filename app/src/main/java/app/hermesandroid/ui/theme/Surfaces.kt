package app.hermesandroid.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/** The app canvas: plain matte graphite (no glows, no grid; the user turned both down). */
@Composable
fun Backdrop(modifier: Modifier = Modifier) {
    val c = C.colors
    Box(modifier.drawBehind { drawRect(c.canvas) })
}

/** Flips each time the app leaves and comes back: a key for things to read again on return (the clock, system settings). */
@Composable
fun resumeKey(): Boolean = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)

val LocalReducedMotion = compositionLocalOf { false }

/** Read once at the theme root (AfTheme), again on each return to the app. */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember(resumeKey()) {
        val scale = Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        scale == 0f || ctx.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    }
}

/** System animations off or battery saver: ambient motion (seams, rings) holds still, things only fade. */
@Composable
fun reducedMotion(): Boolean = LocalReducedMotion.current

/**
 * A lazy row that fades in, glides to its new place when the list changes, and fades out. Only the fades under reduced
 * motion, and for rows trailing a growing reply ([glide] off), which would otherwise chase every new line.
 */
@Composable
fun LazyItemScope.listItem(glide: Boolean = true): Modifier =
    Modifier.animateItem(fadeInSpec = Motion.quick(), placementSpec = if (glide && !reducedMotion()) Motion.enter() else null, fadeOutSpec = Motion.quick())

/** A keyed lazy row with the app's list motion. The key is required: without one, animateItem follows the index. */
fun LazyListScope.row(key: Any, glide: Boolean = true, content: @Composable () -> Unit) =
    item(key = key) { Box(listItem(glide)) { content() } }

fun <T> LazyListScope.rows(items: List<T>, key: (T) -> Any, content: @Composable (T) -> Unit) =
    items(items, key = key) { Box(listItem()) { content(it) } }

/** Loading / content / empty / error and other either-or content: a short crossfade instead of a cut. */
@Composable
fun <S> Swap(state: S, modifier: Modifier = Modifier, contentAlignment: Alignment = Alignment.TopStart, content: @Composable (S) -> Unit) =
    AnimatedContent(
        state, modifier, contentAlignment = contentAlignment, label = "swap",
        transitionSpec = { fadeIn(Motion.quick()) togetherWith fadeOut(Motion.quick()) using SizeTransform(clip = false) { _, _ -> Motion.enter() } },
    ) { content(it) }

/**
 * Chrome that comes and goes (banners, chips, buttons): fades in rising 8 dp. With [resize] the space it takes opens and
 * closes too, so neighbours glide instead of jumping; turn it off for overlays.
 */
@Composable
fun Appear(visible: Boolean, modifier: Modifier = Modifier, resize: Boolean = true, content: @Composable AnimatedVisibilityScope.() -> Unit) =
    appear(visible, modifier, resize, content)

/** For nullable content: keeps the last value while it fades out, so it doesn't empty itself on the way. */
@Composable
fun <T : Any> AppearWith(value: T?, modifier: Modifier = Modifier, resize: Boolean = true, content: @Composable (T) -> Unit) {
    var last by remember { mutableStateOf(value) }
    if (value != null) last = value
    appear(value != null, modifier, resize) { last?.let { content(it) } }
}

/** Fades in rising Motion.rise as [progress] goes 0 → 1, read in the layer (no recomposition); only the fade under reduced motion. */
@Composable
fun Modifier.rise(progress: () -> Float): Modifier {
    val still = reducedMotion()
    return graphicsLayer {
        val k = progress().coerceIn(0f, 1f)
        alpha = k
        translationY = if (still) 0f else (1f - k) * Motion.rise.toPx()
    }
}

/** In a column (also one nested in a row, whose scope would otherwise win). */
@Composable
fun ColumnScope.Appear(visible: Boolean, modifier: Modifier = Modifier, content: @Composable AnimatedVisibilityScope.() -> Unit) =
    appear(visible, modifier, true, content)

@Composable
private fun appear(visible: Boolean, modifier: Modifier, resize: Boolean, content: @Composable AnimatedVisibilityScope.() -> Unit) {
    val shift = if (reducedMotion()) 0 else with(LocalDensity.current) { Motion.rise.roundToPx() }
    var enter = fadeIn(Motion.quick()) + slideInVertically(Motion.enter()) { shift }
    var exit = fadeOut(Motion.quick())
    if (resize) {
        enter += expandVertically(Motion.enter())
        exit += shrinkVertically(Motion.enter())
    }
    AnimatedVisibility(visible, modifier, enter = enter, exit = exit, content = content)
}

/** In a row the space opens sideways, so the neighbours (a weighted title, the field) glide instead of jumping. */
@Composable
fun RowScope.Appear(visible: Boolean, modifier: Modifier = Modifier, content: @Composable AnimatedVisibilityScope.() -> Unit) =
    AnimatedVisibility(
        visible, modifier, content = content,
        enter = fadeIn(Motion.quick()) + expandHorizontally(Motion.enter()), exit = fadeOut(Motion.quick()) + shrinkHorizontally(Motion.enter()),
    )

/** Floating chrome (capsules over content): translucent graphite with a hairline rim. */
fun Modifier.glass(shape: Shape, tint: Color? = null, prominent: Boolean = false): Modifier = composed {
    val c = C.colors
    this
        .clip(shape)
        .background(if (prominent && tint != null) tint.copy(alpha = 0.92f) else c.raised.copy(alpha = 0.86f))
        .then(if (!prominent && tint != null) Modifier.background(tint) else Modifier)
        .border(Dp.Hairline, c.composerStroke, shape)
}

/** The screen content that floating chrome blurs (MainActivity.Root marks it with `hazeSource`). */
val LocalHaze = staticCompositionLocalOf<HazeState?> { null }

/**
 * Frosted glass for chrome floating over scrolling content (tab capsule, top-bar capsule): real blur of what's
 * behind on API 31+, Haze's translucent fallback below. Without a [LocalHaze] source it is plain [glass].
 */
fun Modifier.frosted(shape: Shape): Modifier = composed {
    val state = LocalHaze.current ?: return@composed glass(shape)
    val c = C.colors
    this
        .clip(shape)
        .hazeBlur(HazeInput.Backdrop(state), HazeBlurStyle {
            blurRadius(28.dp)
            backgroundColor(c.canvas)
            colorEffects(listOf(HazeColorEffect.tint(c.raised.copy(alpha = 0.62f))))
            noiseFactor(0.08f)
        })
        .border(Dp.Hairline, c.composerStroke, shape)
}

/** A soft graphite card: one step above the canvas, no stroke. */
fun Modifier.settingsCard(shape: Shape, tint: Color? = null): Modifier = composed {
    val c = C.colors
    this
        .clip(shape)
        .background(c.surface)
        .then(if (tint != null) Modifier.background(tint) else Modifier)
}

/** A raised well with a hairline stroke: code, headers and asks set into a card, sheet or page. */
fun Modifier.well(shape: Shape): Modifier = composed {
    val c = C.colors
    background(c.raised, shape).border(Dp.Hairline, c.hairline, shape)
}

/** Fades what this draws into the canvas, from `from` (0 = top) to the bottom edge: hero portraits. */
fun Modifier.fadeToCanvas(from: Float = 0.5f): Modifier = composed {
    val canvas = C.colors.canvas
    drawWithCache {
        val fade = Brush.verticalGradient(from to Color.Transparent, 1f to canvas)
        onDrawWithContent { drawContent(); drawRect(fade) }
    }
}

/** Every tappable scales to 0.95 on the overshoot spring while pressed. */
fun Modifier.pressable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    label: String? = null,
    haptic: Haptic? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, Motion.overshoot(), label = "press")
    val view = LocalView.current
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(source, null, enabled = enabled, onClickLabel = label, role = role, onLongClick = onLongClick) {
            haptic?.perform(view)
            onClick()
        }
}

/** The haptic vocabulary mapped onto View haptic constants. */
enum class Haptic {
    Selection, Light, Medium, Warning, Success;

    fun perform(view: View) {
        if (!DisplayPrefs.haptics) return
        val r = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        view.performHapticFeedback(
            when (this) {
                Selection -> HapticFeedbackConstants.CLOCK_TICK
                Light -> HapticFeedbackConstants.KEYBOARD_TAP
                Medium -> HapticFeedbackConstants.CONTEXT_CLICK
                Warning -> if (r) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
                Success -> if (r) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
            }
        )
    }
}

