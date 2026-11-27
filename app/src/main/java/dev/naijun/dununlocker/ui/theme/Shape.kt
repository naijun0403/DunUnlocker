package dev.naijun.dununlocker.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.capsule.lerp

/**
 * The Material 3 Expressive shape scale, drawn with G2-continuous corners so the curvature eases
 * into each straight edge instead of changing abruptly where a circular arc meets it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val AppShapes = Shapes(
    extraSmall = ContinuousRoundedRectangle(4.dp),
    small = ContinuousRoundedRectangle(8.dp),
    medium = ContinuousRoundedRectangle(12.dp),
    large = ContinuousRoundedRectangle(16.dp),
    extraLarge = ContinuousRoundedRectangle(28.dp),
    largeIncreased = ContinuousRoundedRectangle(20.dp),
    extraLargeIncreased = ContinuousRoundedRectangle(32.dp),
    extraExtraLarge = ContinuousRoundedRectangle(48.dp),
)

private val FullCorner = CornerSize(50)

/** Shape of item [index] of [count] in a vertical group: round outer corners, tight inner ones. */
internal fun segmentedShape(
    index: Int,
    count: Int,
    outer: Dp = 24.dp,
    inner: Dp = 6.dp,
): ContinuousRoundedRectangle {
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return ContinuousRoundedRectangle(
        topStart = top,
        topEnd = top,
        bottomEnd = bottom,
        bottomStart = bottom,
    )
}

/**
 * List item shapes for a segmented group that keep one G2 shape in every state. Material would
 * otherwise morph these shapes on press, which it can only interpolate for RoundedCornerShape.
 */
@Composable
internal fun segmentedListItemShapes(index: Int, count: Int): ListItemShapes {
    val shape = segmentedShape(index, count)
    return ListItemDefaults.shapes(
        shape = shape,
        selectedShape = shape,
        pressedShape = shape,
        focusedShape = shape,
        hoveredShape = shape,
        draggedShape = shape,
    )
}

/**
 * Shape of a button in a horizontal connected group. The checked button becomes a full capsule,
 * and pressing a button tightens its inner corners, as in Material 3 Expressive button groups.
 */
internal fun connectedButtonShape(
    index: Int,
    count: Int,
    checked: Boolean,
    pressed: Boolean,
): ContinuousRoundedRectangle {
    val inner = CornerSize(if (pressed) 4.dp else 8.dp)
    val start = if (index == 0 || (checked && !pressed)) FullCorner else inner
    val end = if (index == count - 1 || (checked && !pressed)) FullCorner else inner
    return ContinuousRoundedRectangle(
        topStart = start,
        topEnd = end,
        bottomEnd = end,
        bottomStart = start,
    )
}

/**
 * Animates to [target] with Capsule's lerp, which keeps G2 continuity through the morph.
 *
 * The returned shape is not a CornerBasedShape, so Material components draw it as-is instead of
 * running their own morph on top, which would snap between G2 shapes halfway through.
 */
@Composable
internal fun animateContinuousShape(target: ContinuousRoundedRectangle): Shape {
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val progress = remember { Animatable(1f) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        // Start from wherever an interrupted morph currently is.
        from = lerp(from, to, progress.value)
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, spec)
        from = target
    }
    val shape = lerp(from, to, progress.value)
    return remember(shape) { DrawAsIsShape(shape) }
}

/** A capsule that squares off slightly while pressed, like Material 3 Expressive buttons. */
@Composable
internal fun pressMorphShape(
    interactionSource: InteractionSource,
    pressedCorner: Dp = 16.dp,
): Shape {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateContinuousShape(
        if (pressed) ContinuousRoundedRectangle(pressedCorner) else ContinuousCapsule
    )
}

private class DrawAsIsShape(private val shape: Shape) : Shape by shape
