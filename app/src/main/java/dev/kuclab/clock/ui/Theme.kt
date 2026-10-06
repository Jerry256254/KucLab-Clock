package dev.kuclab.clock.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.kuclab.clock.R
import dev.kuclab.clock.WakeScene

/** Deep night palette with cool glass highlights. It stays dark at every system setting so
 * opening the clock beside the bed never produces a white flash. */
data class ClockColors(
    val bg: Color,
    val surface: Color,
    val card: Color,
    val hairline: Color,
    val ink: Color,
    val muted: Color,
    val trackBg: Color,
    val accent: Color,
    val onAccent: Color,
    val err: Color
)

val MidnightColors = ClockColors(
    bg = Color(0xFF070A0F),
    surface = Color(0xFF0D111B),
    card = Color(0xFF151C2A),
    hairline = Color(0xFF33405A),
    ink = Color(0xFFF4F7FC),
    muted = Color(0xFF929DB2),
    trackBg = Color(0xFF222B3C),
    accent = Color(0xFF8DB6FF),
    onAccent = Color(0xFF07101F),
    err = Color(0xFFFF7F7A)
)

val LocalClockColors = staticCompositionLocalOf { MidnightColors }

/** These read through the composition local, so referencing `Ink`/`Accent`/etc. anywhere
 * in the app automatically follows the current light/dark palette - no plumbing needed at
 * call sites beyond staying inside a @Composable. */
val Ink: Color @Composable @ReadOnlyComposable get() = LocalClockColors.current.bg
val SurfaceBg: Color @Composable @ReadOnlyComposable get() = LocalClockColors.current.surface
val CardBg: Color @Composable @ReadOnlyComposable get() = LocalClockColors.current.card
val Hairline: Color @Composable @ReadOnlyComposable get() = LocalClockColors.current.hairline
val OnDark: Color @Composable @ReadOnlyComposable get() = LocalClockColors.current.ink
val Muted: Color @Composable @ReadOnlyComposable get() = LocalClockColors.current.muted
val TrackBg: Color @Composable @ReadOnlyComposable get() = LocalClockColors.current.trackBg
val Accent: Color @Composable @ReadOnlyComposable get() = LocalClockColors.current.accent
val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalClockColors.current.onAccent
val ErrRed: Color @Composable @ReadOnlyComposable get() = LocalClockColors.current.err

private val ClockShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

// A single variable font (weight axis) standing in for the whole Urbanist family - one
// bundled .ttf, every weight below just dials in a different FontVariation.weight().
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Urbanist = FontFamily(
    Font(R.font.urbanist, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.urbanist, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.urbanist, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.urbanist, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.urbanist, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

// Every Text() in the app that doesn't explicitly set its own fontFamily (i.e. everything
// except the live-ticking numeric displays, which stay FontFamily.Monospace on purpose so
// digits don't jitter width as they change) inherits Urbanist through this.
private val ClockTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Urbanist),
        displayMedium = base.displayMedium.copy(fontFamily = Urbanist),
        displaySmall = base.displaySmall.copy(fontFamily = Urbanist),
        headlineLarge = base.headlineLarge.copy(fontFamily = Urbanist),
        headlineMedium = base.headlineMedium.copy(fontFamily = Urbanist),
        headlineSmall = base.headlineSmall.copy(fontFamily = Urbanist),
        titleLarge = base.titleLarge.copy(fontFamily = Urbanist),
        titleMedium = base.titleMedium.copy(fontFamily = Urbanist),
        titleSmall = base.titleSmall.copy(fontFamily = Urbanist),
        bodyLarge = base.bodyLarge.copy(fontFamily = Urbanist),
        bodyMedium = base.bodyMedium.copy(fontFamily = Urbanist),
        bodySmall = base.bodySmall.copy(fontFamily = Urbanist),
        labelLarge = base.labelLarge.copy(fontFamily = Urbanist),
        labelMedium = base.labelMedium.copy(fontFamily = Urbanist),
        labelSmall = base.labelSmall.copy(fontFamily = Urbanist)
    )
}

@Composable
fun KucLabTheme(content: @Composable () -> Unit) {
    // The clock is primarily used in dark rooms. A single carefully tuned dark palette
    // avoids the harsh white flash a system-theme switch can cause around wake-up time.
    val palette = MidnightColors
    val scheme = darkColorScheme(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        secondary = palette.accent,
        onSecondary = palette.onAccent,
        secondaryContainer = palette.card,
        onSecondaryContainer = palette.ink,
        background = palette.bg,
        onBackground = palette.ink,
        surface = palette.bg,
        onSurface = palette.ink,
        surfaceVariant = palette.card,
        onSurfaceVariant = palette.muted,
        error = palette.err,
        outline = palette.hairline
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalClockColors provides palette) {
        MaterialTheme(colorScheme = scheme, shapes = ClockShapes, typography = ClockTypography, content = content)
    }
}

/** Flat surface: 1px hairline border, solid fill, no shadow/gradient/blur. */
@Composable
fun Modifier.hairlineCard(shape: Shape = RoundedCornerShape(20.dp), fill: Color = CardBg, borderAlpha: Float = 1f): Modifier =
    this
        .clip(shape)
        .background(
            if (fill == CardBg) {
                Brush.verticalGradient(
                    listOf(fill.copy(alpha = 0.88f), fill.copy(alpha = 0.66f))
                )
            } else {
                Brush.linearGradient(listOf(fill, fill))
            }
        )
        .border(width = 1.dp, color = Hairline.copy(alpha = Hairline.alpha * borderAlpha), shape = shape)

/** Restrained glass surface: translucent depth and a fine highlight, without fake blur or
 * decorative chrome that would compete with an alarm's controls. */
@Composable
fun Modifier.glassCard(shape: Shape = RoundedCornerShape(24.dp)): Modifier =
    this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(CardBg.copy(alpha = 0.84f), SurfaceBg.copy(alpha = 0.68f))
            )
        )
        .border(1.dp, Hairline.copy(alpha = 0.72f), shape)

private data class ScenePalette(val top: Color, val bottom: Color, val glow: Color, val secondGlow: Color)

private fun scenePalette(scene: WakeScene): ScenePalette = when (scene) {
    WakeScene.AURORA -> ScenePalette(Color(0xFF07101A), Color(0xFF090B12), Color(0xFF2D8C91), Color(0xFF6E5DB7))
    WakeScene.DAWN -> ScenePalette(Color(0xFF18101A), Color(0xFF090B10), Color(0xFFD56E55), Color(0xFF8A4A78))
    WakeScene.DEEP -> ScenePalette(Color(0xFF07101C), Color(0xFF05070C), Color(0xFF235184), Color(0xFF27365F))
    WakeScene.EMBER -> ScenePalette(Color(0xFF180D0B), Color(0xFF08090D), Color(0xFFCC6438), Color(0xFF79522C))
}

@Composable
fun WakeSceneBackground(
    scene: WakeScene,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val p = scenePalette(scene)
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(p.top, p.bottom)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(p.glow.copy(alpha = 0.24f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.12f),
                    radius = size.width * 0.95f
                ),
                radius = size.width * 0.95f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.12f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(p.secondGlow.copy(alpha = 0.16f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.94f, size.height * 0.78f),
                    radius = size.width * 0.82f
                ),
                radius = size.width * 0.82f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.94f, size.height * 0.78f)
            )
        }
        content()
    }
}

@Composable
fun AppBackdrop(content: @Composable BoxScope.() -> Unit) =
    WakeSceneBackground(WakeScene.DEEP, content = content)

@Composable
fun Modifier.hairlinePill(): Modifier = hairlineCard(shape = RoundedCornerShape(50), fill = Ink)

// Two intensities of haptic tick, kept to the stable HapticFeedbackType API.
fun HapticFeedback.tap() = performHapticFeedback(HapticFeedbackType.TextHandleMove)
fun HapticFeedback.confirm() = performHapticFeedback(HapticFeedbackType.LongPress)

/** A calm, slightly bouncy spring used everywhere a value animates in this app - dials,
 * sliders, progress rings, tab crossfades - so motion feels like one consistent material
 * rather than a grab-bag of easing curves. */
fun <T> motionSpring(): SpringSpec<T> =
    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)

/** Tracks a 0.96x "press" scale off the given interaction source's pressed state. Apply the
 * result via `Modifier.graphicsLayer { scaleX = f; scaleY = f }` on the pressable element. */
@Composable
fun rememberPressScale(interactionSource: MutableInteractionSource): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(if (pressed) 0.96f else 1f, motionSpring(), label = "pressScale")
}

/** Convenience wrapper: scales down slightly whenever the element is pressed. */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val scale by rememberPressScale(interactionSource)
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}
