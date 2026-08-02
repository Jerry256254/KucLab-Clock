package dev.kuclab.clock.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/**
 * Warm, paper-like palette in the spirit of the Claude app: a clay/terracotta accent on a
 * cream (light) or warm charcoal (dark) ground - never cold neutral gray, never a gradient.
 * Both variants follow the system light/dark setting so the app reads naturally on a
 * nightstand at 2am and on a desk at noon alike.
 */
data class ClaudeColors(
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

val ClaudeLight = ClaudeColors(
    bg = Color(0xFFF9F7F2),
    surface = Color(0xFFF3F1E9),
    card = Color(0xFFFFFFFF),
    hairline = Color(0xFFE7E3D6),
    ink = Color(0xFF27251F),
    muted = Color(0xFF8B876F),
    trackBg = Color(0xFFECE8DC),
    accent = Color(0xFFD97757),
    onAccent = Color(0xFFFFFFFF),
    err = Color(0xFFC4432E)
)

val ClaudeDark = ClaudeColors(
    bg = Color(0xFF211F1B),
    surface = Color(0xFF29271F),
    card = Color(0xFF2E2C24),
    hairline = Color(0xFF3D3A30),
    ink = Color(0xFFF3EFE4),
    muted = Color(0xFF9C9686),
    trackBg = Color(0xFF3A372C),
    accent = Color(0xFFE08659),
    onAccent = Color(0xFF211F1B),
    err = Color(0xFFE5715A)
)

val LocalClaudeColors = staticCompositionLocalOf { ClaudeLight }

/** These read through the composition local, so referencing `Ink`/`Accent`/etc. anywhere
 * in the app automatically follows the current light/dark palette - no plumbing needed at
 * call sites beyond staying inside a @Composable. */
val Ink: Color @Composable @ReadOnlyComposable get() = LocalClaudeColors.current.bg
val SurfaceBg: Color @Composable @ReadOnlyComposable get() = LocalClaudeColors.current.surface
val CardBg: Color @Composable @ReadOnlyComposable get() = LocalClaudeColors.current.card
val Hairline: Color @Composable @ReadOnlyComposable get() = LocalClaudeColors.current.hairline
val OnDark: Color @Composable @ReadOnlyComposable get() = LocalClaudeColors.current.ink
val Muted: Color @Composable @ReadOnlyComposable get() = LocalClaudeColors.current.muted
val TrackBg: Color @Composable @ReadOnlyComposable get() = LocalClaudeColors.current.trackBg
val Accent: Color @Composable @ReadOnlyComposable get() = LocalClaudeColors.current.accent
val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalClaudeColors.current.onAccent
val ErrRed: Color @Composable @ReadOnlyComposable get() = LocalClaudeColors.current.err

private val ClaudeShapes = Shapes(
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
private val ClaudeTypography = Typography().let { base ->
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
    val palette = if (isSystemInDarkTheme()) ClaudeDark else ClaudeLight
    val scheme = lightColorScheme(
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
    androidx.compose.runtime.CompositionLocalProvider(LocalClaudeColors provides palette) {
        MaterialTheme(colorScheme = scheme, shapes = ClaudeShapes, typography = ClaudeTypography, content = content)
    }
}

/** Flat surface: 1px hairline border, solid fill, no shadow/gradient/blur. */
@Composable
fun Modifier.hairlineCard(shape: Shape = RoundedCornerShape(20.dp), fill: Color = CardBg, borderAlpha: Float = 1f): Modifier =
    this
        .clip(shape)
        .background(fill)
        .border(width = 1.dp, color = Hairline.copy(alpha = Hairline.alpha * borderAlpha), shape = shape)

@Composable
fun Modifier.hairlinePill(): Modifier = hairlineCard(shape = RoundedCornerShape(50), fill = Ink)

// Two intensities of haptic tick, kept to the stable HapticFeedbackType API.
fun HapticFeedback.tap() = performHapticFeedback(HapticFeedbackType.TextHandleMove)
fun HapticFeedback.confirm() = performHapticFeedback(HapticFeedbackType.LongPress)

/** A calm, slightly bouncy spring used everywhere a value animates in this app - dials,
 * sliders, progress rings, tab crossfades - so motion feels like one consistent material
 * rather than a grab-bag of easing curves. */
fun <T> claudeSpring(): SpringSpec<T> =
    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)

/** Tracks a 0.96x "press" scale off the given interaction source's pressed state. Apply the
 * result via `Modifier.graphicsLayer { scaleX = f; scaleY = f }` on the pressable element. */
@Composable
fun rememberPressScale(interactionSource: MutableInteractionSource): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(if (pressed) 0.96f else 1f, claudeSpring(), label = "pressScale")
}

/** Convenience wrapper: scales down slightly whenever the element is pressed. */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val scale by rememberPressScale(interactionSource)
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}
