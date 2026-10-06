package dev.kuclab.clock.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import dev.kuclab.clock.WakeScene

/** A quiet, utility-first palette. The app should feel like an instrument, not chrome. */
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
    bg = Color(0xFF0C0D0F),
    surface = Color(0xFF121417),
    card = Color(0xFF181A1E),
    hairline = Color(0xFF2A2D32),
    ink = Color(0xFFF2F1EC),
    muted = Color(0xFF96989D),
    trackBg = Color(0xFF24272C),
    accent = Color(0xFFFFB45C),
    onAccent = Color(0xFF211406),
    err = Color(0xFFFF7870)
)

val LocalClockColors = staticCompositionLocalOf { MidnightColors }

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
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

@Composable
fun KucLabTheme(content: @Composable () -> Unit) {
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
        surface = palette.surface,
        onSurface = palette.ink,
        surfaceVariant = palette.card,
        onSurfaceVariant = palette.muted,
        error = palette.err,
        outline = palette.hairline
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalClockColors provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = ClockShapes,
            typography = Typography(),
            content = content
        )
    }
}

/** A plain elevated surface used for meaningful groups, never for every individual control. */
@Composable
fun Modifier.hairlineCard(
    shape: Shape = RoundedCornerShape(14.dp),
    fill: Color = CardBg,
    borderAlpha: Float = 1f
): Modifier = this
    .clip(shape)
    .background(fill)
    .border(1.dp, Hairline.copy(alpha = borderAlpha), shape)

/** Compatibility name for old call sites; the surface is intentionally no longer glassy. */
@Composable
fun Modifier.glassCard(shape: Shape = RoundedCornerShape(14.dp)): Modifier =
    hairlineCard(shape = shape)

private data class ScenePalette(val top: Color, val bottom: Color, val glow: Color)

private fun scenePalette(scene: WakeScene): ScenePalette = when (scene) {
    WakeScene.AURORA -> ScenePalette(Color(0xFF0C1213), Color(0xFF090B0D), Color(0xFF4FA09A))
    WakeScene.DAWN -> ScenePalette(Color(0xFF17100D), Color(0xFF0B0B0C), Color(0xFFD68755))
    WakeScene.DEEP -> ScenePalette(Color(0xFF0C0F14), Color(0xFF090A0C), Color(0xFF55759A))
    WakeScene.EMBER -> ScenePalette(Color(0xFF17100B), Color(0xFF0A0A0B), Color(0xFFC86F3D))
}

/** Scene colour is reserved for the actual wake-up flow, where atmosphere has a purpose. */
@Composable
fun WakeSceneBackground(
    scene: WakeScene,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = scenePalette(scene)
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(palette.top, palette.bottom)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(palette.glow.copy(alpha = 0.13f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * .5f, 0f),
                    radius = size.width
                ),
                radius = size.width,
                center = androidx.compose.ui.geometry.Offset(size.width * .5f, 0f)
            )
        }
        content()
    }
}

@Composable
fun AppBackdrop(content: @Composable BoxScope.() -> Unit) =
    Box(Modifier.fillMaxSize().background(Ink), content = content)

@Composable
fun Modifier.hairlinePill(): Modifier =
    hairlineCard(shape = RoundedCornerShape(50), fill = SurfaceBg)

fun HapticFeedback.tap() = performHapticFeedback(HapticFeedbackType.TextHandleMove)
fun HapticFeedback.confirm() = performHapticFeedback(HapticFeedbackType.LongPress)

fun <T> motionSpring(): SpringSpec<T> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

@Composable
fun rememberPressScale(interactionSource: MutableInteractionSource): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(if (pressed) .98f else 1f, motionSpring(), label = "pressScale")
}

@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val scale by rememberPressScale(interactionSource)
    return graphicsLayer { scaleX = scale; scaleY = scale }
}
