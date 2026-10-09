package app.foldcade.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.foldcade.FoldcadeApp
import app.foldcade.language.Chrome
import app.foldcade.language.Copy
import app.foldcade.language.HostScreen
import app.foldcade.language.Meaning
import app.foldcade.language.Metrics
import app.foldcade.language.Side
import app.foldcade.language.SidePanel
import app.foldcade.language.TypeRamp
import app.foldcade.millisUntilNextMinute
import app.foldcade.readDeviceStatus
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Art-kit shoulder chips. Outline while the island is closed, filled while that
 * menu is open. This module does not ship the PNGs.
 * `@drawable/ic_btn_l1`, `@drawable/ic_btn_l1_filled`,
 * `@drawable/ic_btn_r1`, `@drawable/ic_btn_r1_filled`.
 */
internal object ShoulderChips {
    const val l1 = "ic_btn_l1"
    const val l1Filled = "ic_btn_l1_filled"
    const val r1 = "ic_btn_r1"
    const val r1Filled = "ic_btn_r1_filled"
}

@Composable
internal fun TopIslands(
    app: FoldcadeApp,
    screen: HostScreen,
    scale: Float,
    menuRows: @Composable (panel: SidePanel, progress: Float, interactive: Boolean) -> Unit,
) {
    if (screen != HostScreen.Top) return
    val model = app.shell.model
    val dialogCovers = model.dialog?.screen == HostScreen.Top
    BoxWithConstraints(Modifier.fillMaxSize().zIndex(4f)) {
        Island(
            app = app,
            side = Side.Left,
            open = !dialogCovers && model.panel?.side == Side.Left && model.panel?.screen == HostScreen.Top,
            scale = scale,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            menuRows = menuRows,
        )
        Island(
            app = app,
            side = Side.Right,
            open = !dialogCovers && model.panel?.side == Side.Right && model.panel?.screen == HostScreen.Top,
            scale = scale,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            menuRows = menuRows,
        )
    }
}

@Composable
private fun Island(
    app: FoldcadeApp,
    side: Side,
    open: Boolean,
    scale: Float,
    maxWidth: Dp,
    maxHeight: Dp,
    menuRows: @Composable (panel: SidePanel, progress: Float, interactive: Boolean) -> Unit,
) {
    val theme = LocalFoldTheme.current.theme
    val density = LocalDensity.current
    val hold = app.islandHold?.takeIf { it.side == side }?.progress
    val progress = islandProgress(open = open || hold != null, scale = scale, held = hold)
    val live = app.shell.model.panel?.takeIf { it.side == side && it.screen == HostScreen.Top }
    var shown by remember { mutableStateOf<SidePanel?>(null) }
    if (live != null) shown = live
    val panel = shown
    val inset = maxWidth * Metrics.insetFraction
    val pillWidth = if (side == Side.Left) 168.dp else 260.dp
    val pillHeight = 44.dp
    val openWidth = maxWidth * 0.48f
    val openHeight = maxHeight * 0.84f
    val width = lerp(pillWidth, openWidth, progress)
    val height = lerp(pillHeight, openHeight, progress)
    val radius = lerp(pillHeight / 2, 22.dp, progress)
    val shape = RoundedCornerShape(radius)
    val x = if (side == Side.Left) inset else maxWidth - inset - width
    val focusedIsland = progress < 0.08f && app.shell.model.panel == null && app.shell.model.dialog == null &&
        side == Side.Right && app.shell.model.focus.chrome == Chrome.StatusCluster
    val interactive = open && progress > 0.92f
    Box(
        Modifier
            .offset {
                IntOffset(
                    with(density) { x.roundToPx() },
                    with(density) { inset.roundToPx() },
                )
            }
            .size(width, height)
            .zIndex(if (progress > 0f) 2f else 1f)
            .border(1.dp, if (focusedIsland) theme.focus else theme.onBackground.copy(alpha = 0.45f), shape)
            .clip(shape)
            .background(theme.background)
            .then(if (!interactive) Modifier.islandPress { app.shell.onMeaning(side.meaning(), HostScreen.Top) } else Modifier),
    ) {
        Row(
            Modifier
                .align(if (side == Side.Left) Alignment.TopStart else Alignment.TopEnd)
                .height(pillHeight)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (side == Side.Left) {
                ShoulderChip(ShoulderChips.l1, ShoulderChips.l1Filled, progress, "L1")
                Row(Modifier.graphicsLayer { alpha = 1f - progress }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolIcon { sliders(theme.onBackground) }
                    ToolIcon { grid(theme.onBackground) }
                    ToolIcon { note(theme.onBackground) }
                }
            } else {
                Row(Modifier.graphicsLayer { alpha = 1f - progress }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusIcons(app)
                }
                ShoulderChip(ShoulderChips.r1, ShoulderChips.r1Filled, progress, "R1")
            }
        }
        if (panel != null && progress > 0f) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 12.dp, top = pillHeight, bottom = 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                menuRows(panel, progress, interactive)
            }
        }
    }
}

private fun Side.meaning(): Meaning = if (this == Side.Left) Meaning.LeftPanel else Meaning.RightPanel

@Composable
private fun islandProgress(open: Boolean, scale: Float, held: Float?): Float {
    val target = held ?: if (open) 1f else 0f
    val anim = remember { androidx.compose.animation.core.Animatable(target) }
    LaunchedEffect(target, held) {
        if (held != null) {
            anim.snapTo(held)
        } else {
            kotlinx.coroutines.withContext(SteadyIsland) {
                anim.animateTo(
                    target,
                    if (target >= anim.value) {
                        app.foldcade.language.Motion.arrive(app.foldcade.language.Motion.durationIsland, scale)
                    } else {
                        app.foldcade.language.Motion.leave(app.foldcade.language.Motion.durationIsland, scale)
                    },
                )
            }
        }
    }
    return anim.value
}

private object SteadyIsland : androidx.compose.ui.MotionDurationScale {
    override val scaleFactor: Float = 1f
}

/**
 * Cross-fades `@drawable/ic_btn_l1` or `@drawable/ic_btn_r1` into the `_filled` variant.
 * Missing art leaves the slot empty. This does not draw a substitute chip.
 */
@Composable
private fun ShoulderChip(outlineName: String, filledName: String, openFraction: Float, label: String) {
    val context = LocalContext.current
    val outline = remember(outlineName) { drawableId(context, outlineName) }
    val filled = remember(filledName) { drawableId(context, filledName) }
    Box(Modifier.size(40.dp, 28.dp), contentAlignment = Alignment.Center) {
        if (outline != 0) {
            Image(
                painter = painterResource(outline),
                contentDescription = label,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - openFraction },
                contentScale = ContentScale.Fit,
            )
        }
        if (filled != 0) {
            Image(
                painter = painterResource(filled),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = openFraction },
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun drawableId(context: Context, name: String): Int =
    context.resources.getIdentifier(name, "drawable", context.packageName)

@Composable
private fun StatusIcons(app: FoldcadeApp) {
    val theme = LocalFoldTheme.current.theme
    val context = LocalContext.current
    var status by remember { mutableStateOf(readDeviceStatus(context)) }
    LaunchedEffect(context) {
        while (true) {
            status = readDeviceStatus(context)
            delay(millisUntilNextMinute(System.currentTimeMillis()))
        }
    }
    val notices = app.shell.model.notices.size
    BasicText(
        text = status.time,
        style = TextStyle(color = theme.onBackground, fontSize = TypeRamp.hint, fontFamily = theme.font),
    )
    ToolIcon { battery(theme.onBackground, status.batteryPercent, status.charging) }
    ToolIcon { wifi(if (status.network == Copy.wifi) theme.onBackground else theme.onBackground.copy(alpha = 0.4f)) }
    Box(contentAlignment = Alignment.TopEnd) {
        ToolIcon { bell(theme.onBackground) }
        if (notices > 0) {
            BasicText(
                text = notices.coerceAtMost(9).toString(),
                style = TextStyle(color = theme.focus, fontSize = TypeRamp.hint, fontFamily = theme.font),
            )
        } else {
            Spacer(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(5.dp)
                    .background(theme.focus, CircleShape),
            )
        }
    }
}

@Composable
private fun ToolIcon(draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    Canvas(Modifier.size(18.dp), onDraw = draw)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.sliders(color: Color) {
    val gaps = listOf(0.22f, 0.5f, 0.78f)
    val knobs = listOf(0.32f, 0.68f, 0.42f)
    gaps.forEachIndexed { index, yFrac ->
        val y = size.height * yFrac
        drawLine(color, Offset(size.width * 0.12f, y), Offset(size.width * 0.88f, y), strokeWidth = 1.6f, cap = StrokeCap.Round)
        drawCircle(color, radius = 2.2f, center = Offset(size.width * knobs[index], y))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.grid(color: Color) {
    val stroke = Stroke(width = 1.5f)
    val cells = listOf(0.12f to 0.12f, 0.54f to 0.12f, 0.12f to 0.54f, 0.54f to 0.54f)
    cells.forEach { (x, y) ->
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * x, size.height * y),
            size = Size(size.width * 0.32f, size.height * 0.32f),
            cornerRadius = CornerRadius(2f, 2f),
            style = stroke,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.note(color: Color) {
    val head = Offset(size.width * 0.34f, size.height * 0.72f)
    drawCircle(color, radius = size.minDimension * 0.16f, center = head)
    drawLine(
        color,
        head + Offset(size.minDimension * 0.14f, -size.minDimension * 0.08f),
        head + Offset(size.minDimension * 0.14f, -size.minDimension * 0.48f),
        strokeWidth = 1.6f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color,
        head + Offset(size.minDimension * 0.14f, -size.minDimension * 0.48f),
        head + Offset(size.minDimension * 0.36f, -size.minDimension * 0.36f),
        strokeWidth = 1.6f,
        cap = StrokeCap.Round,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.bell(color: Color) {
    val stroke = Stroke(width = 1.6f, cap = StrokeCap.Round)
    drawArc(
        color = color,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(size.width * 0.18f, size.height * 0.16f),
        size = Size(size.width * 0.64f, size.height * 0.64f),
        style = stroke,
    )
    drawLine(
        color,
        Offset(size.width * 0.2f, size.height * 0.68f),
        Offset(size.width * 0.8f, size.height * 0.68f),
        strokeWidth = 1.6f,
        cap = StrokeCap.Round,
    )
    drawCircle(color, radius = 1.5f, center = Offset(size.width * 0.5f, size.height * 0.82f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.wifi(color: Color) {
    val stroke = Stroke(width = 1.6f, cap = StrokeCap.Round)
    val cx = size.width / 2f
    val base = size.height * 0.78f
    drawCircle(color, radius = 1.6f, center = Offset(cx, base))
    listOf(0.28f, 0.46f, 0.66f).forEach { scale ->
        val radius = size.minDimension * scale
        drawArc(
            color = color,
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(cx - radius, base - radius),
            size = Size(radius * 2f, radius * 2f),
            style = stroke,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.battery(color: Color, percent: Int, charging: Boolean) {
    val stroke = Stroke(width = 1.5f)
    val body = Size(size.width * 0.72f, size.height * 0.48f)
    val top = Offset(size.width * 0.08f, (size.height - body.height) / 2f)
    drawRoundRect(color, topLeft = top, size = body, cornerRadius = CornerRadius(2f, 2f), style = stroke)
    val fillWidth = body.width * (percent.coerceIn(0, 100) / 100f) * 0.8f
    if (fillWidth > 0f) {
        drawRoundRect(
            color,
            topLeft = top + Offset(body.width * 0.1f, body.height * 0.18f),
            size = Size(fillWidth, body.height * 0.64f),
            cornerRadius = CornerRadius(1f, 1f),
        )
    }
    drawRect(
        color,
        topLeft = Offset(top.x + body.width, top.y + body.height * 0.3f),
        size = Size(size.width * 0.08f, body.height * 0.4f),
    )
    if (charging) {
        drawLine(
            color,
            Offset(size.width * 0.42f, size.height * 0.34f),
            Offset(size.width * 0.34f, size.height * 0.66f),
            strokeWidth = 1.4f,
        )
    }
}

@Composable
private fun Modifier.islandPress(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .focusProperties { canFocus = false }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

private fun lerp(start: Dp, end: Dp, fraction: Float): Dp = start + (end - start) * fraction
