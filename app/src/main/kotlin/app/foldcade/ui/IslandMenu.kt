package app.foldcade.ui

import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.foldcade.FoldcadeApp
import app.foldcade.R
import app.foldcade.api.Panel
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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Art-kit shoulder chips. Outline while the island is closed, filled while that
 * menu is open.
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
    // Top screen only. The bottom screen keeps the grid and the hint row.
    // The L1 chip, launch target, and status cluster are not drawn there.
    if (screen != HostScreen.Top) return
    val model = app.shell.model
    val dialogCovers = model.dialog?.screen == HostScreen.Top
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .zIndex(4f)
            .focusProperties { canFocus = false },
    ) {
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
    val model = app.shell.model
    val held = app.islandHold
    val mine = model.panel?.takeIf { it.side == side && it.screen == HostScreen.Top }
    val retiringHere = held == null && mine?.retiring == true
    val snap = held != null
    val target = when {
        held != null && held.side == side -> held.progress
        held != null || retiringHere -> 0f
        mine != null -> 1f
        else -> 0f
    }
    val progress = islandProgress(
        target = target,
        scale = scale,
        snap = snap,
        handoff = retiringHere,
        onHandoff = { app.shell.completeIslandRetire() },
    )
    val live = app.shell.model.panel?.takeIf { it.side == side && it.screen == HostScreen.Top }
    var shown by remember { mutableStateOf<SidePanel?>(null) }
    if (live != null) shown = live
    val panel = shown
    val inset = maxWidth * Metrics.insetFraction
    val pillWidth = when {
        side == Side.Right -> 260.dp
        launchTargetShown(app) -> 280.dp
        else -> 72.dp
    }
    val pillHeight = 44.dp
    val openWidth = maxWidth * 0.48f
    val openHeight = maxHeight * 0.84f
    val width = lerp(pillWidth, openWidth, progress)
    val height = lerp(pillHeight, openHeight, progress)
    val radius = lerp(pillHeight / 2, Metrics.cardCornerDp.dp, progress)
    val shape = RoundedCornerShape(radius)
    val x = if (side == Side.Left) inset else maxWidth - inset - width
    val focusedIsland = progress < 0.08f && app.shell.model.panel == null && app.shell.model.dialog == null &&
        when (side) {
            Side.Left -> app.shell.model.focus.chrome == Chrome.LaunchTarget
            Side.Right -> app.shell.model.focus.chrome == Chrome.StatusCluster
        }
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
            // A filled card. A faint edge keeps it apart from a black backdrop.
            .border(
                if (focusedIsland) 2.dp else 1.dp,
                if (focusedIsland) theme.focus else theme.onBackground.copy(alpha = 0.08f),
                shape,
            )
            .clip(shape)
            .background(theme.surface)
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
                ShoulderChip(R.drawable.ic_btn_l1, R.drawable.ic_btn_l1_filled, progress, "L1")
                Row(
                    Modifier.graphicsLayer { alpha = 1f - progress },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LaunchTargetLabel(app, progress)
                }
            } else {
                Row(Modifier.graphicsLayer { alpha = 1f - progress }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusIcons(app)
                }
                ShoulderChip(R.drawable.ic_btn_r1, R.drawable.ic_btn_r1_filled, progress, "R1")
            }
        }
        if (panel != null && progress > 0f) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 12.dp, top = pillHeight, bottom = 8.dp)
                    .verticalScroll(rememberScrollState())
                    // The scroll clips. This keeps the focus ring on the first and last rows inside it.
                    .padding(vertical = 10.dp),
            ) {
                menuRows(panel, progress, interactive)
            }
        }
    }
}

private fun Side.meaning(): Meaning = if (this == Side.Left) Meaning.LeftPanel else Meaning.RightPanel

/** Closed left island shows the launch target. The menu opening does not change the pill width. */
private fun launchTargetShown(app: FoldcadeApp): Boolean {
    val model = app.shell.model
    if (model.dialog != null || model.connectOpen) return false
    val game = app.shell.focusedGame() ?: return false
    if (game.emptyShelfHint) return false
    return app.store.session.launchTargetControlVisible(game.occupiesBothDisplays)
}

@Composable
private fun LaunchTargetLabel(app: FoldcadeApp, progress: Float) {
    if (!launchTargetShown(app)) return
    val game = app.shell.focusedGame() ?: return
    val theme = LocalFoldTheme.current.theme
    val model = app.shell.model
    val target = app.store.session.singleScreenTarget(game.id, game.platformId)
    val label = if (target == Panel.Bottom || model.launchOnBottom) Copy.launchOnBottom else Copy.launchOnTop
    val focused = model.focus.chrome == Chrome.LaunchTarget
    BasicText(
        text = label,
        modifier = if (progress < 0.08f) {
            Modifier.islandPress { app.shell.touchChrome(Chrome.LaunchTarget, HostScreen.Top) }
        } else {
            Modifier
        },
        style = TextStyle(
            color = if (focused) theme.focus else theme.onBackground,
            fontSize = TypeRamp.hint,
            fontFamily = theme.font,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun islandProgress(
    target: Float,
    scale: Float,
    snap: Boolean,
    handoff: Boolean,
    onHandoff: () -> Unit,
): Float {
    val anim = remember { androidx.compose.animation.core.Animatable(target) }
    LaunchedEffect(target, snap, handoff) {
        if (snap) {
            anim.snapTo(target)
            return@LaunchedEffect
        }
        if (abs(anim.value - target) > 0.001f) {
            val spec = if (target >= anim.value) {
                app.foldcade.language.Motion.arrive(app.foldcade.language.Motion.durationIsland, scale)
            } else {
                app.foldcade.language.Motion.leave(app.foldcade.language.Motion.durationIsland, scale)
            }
            val budget = app.foldcade.language.Motion.duration(
                app.foldcade.language.Motion.durationIsland,
                scale,
            ).toLong() + 80L
            withTimeoutOrNull(budget) {
                kotlinx.coroutines.withContext(SteadyIsland) {
                    anim.animateTo(target, spec)
                }
            }
            if (abs(anim.value - target) > 0.02f) anim.snapTo(target)
        }
        if (handoff && anim.value <= 0.02f) onHandoff()
    }
    return if (snap) target else anim.value
}

private object SteadyIsland : androidx.compose.ui.MotionDurationScale {
    override val scaleFactor: Float = 1f
}

/**
 * Cross-fades `@drawable/ic_btn_l1` or `@drawable/ic_btn_r1` into the `_filled` variant.
 */
@Composable
private fun ShoulderChip(outline: Int, filled: Int, openFraction: Float, label: String) {
    Box(Modifier.size(36.dp, 24.dp), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(outline),
            contentDescription = label,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - openFraction },
            contentScale = ContentScale.Fit,
        )
        Image(
            painter = painterResource(filled),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = openFraction },
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun Glyph(id: Int, description: String?) {
    Image(
        painter = painterResource(id),
        contentDescription = description,
        modifier = Modifier.size(18.dp),
        contentScale = ContentScale.Fit,
    )
}

/** Battery, Wi-Fi, and the bell are art-kit icons. The clock stays text. */
internal fun batteryStatusIcon(percent: Int, charging: Boolean): Int {
    val bucket = ((percent.coerceIn(0, 100) + 5) / 10 * 10).coerceAtMost(100)
    return if (charging) {
        when (bucket) {
            0 -> R.drawable.ic_status_battery_0_charging
            10 -> R.drawable.ic_status_battery_10_charging
            20 -> R.drawable.ic_status_battery_20_charging
            30 -> R.drawable.ic_status_battery_30_charging
            40 -> R.drawable.ic_status_battery_40_charging
            50 -> R.drawable.ic_status_battery_50_charging
            60 -> R.drawable.ic_status_battery_60_charging
            70 -> R.drawable.ic_status_battery_70_charging
            80 -> R.drawable.ic_status_battery_80_charging
            90 -> R.drawable.ic_status_battery_90_charging
            else -> R.drawable.ic_status_battery_100_charging
        }
    } else {
        when (bucket) {
            0 -> R.drawable.ic_status_battery_0
            10 -> R.drawable.ic_status_battery_10
            20 -> R.drawable.ic_status_battery_20
            30 -> R.drawable.ic_status_battery_30
            40 -> R.drawable.ic_status_battery_40
            50 -> R.drawable.ic_status_battery_50
            60 -> R.drawable.ic_status_battery_60
            70 -> R.drawable.ic_status_battery_70
            80 -> R.drawable.ic_status_battery_80
            90 -> R.drawable.ic_status_battery_90
            else -> R.drawable.ic_status_battery_100
        }
    }
}

internal fun wifiStatusIcon(network: String): Int =
    if (network == Copy.wifi) R.drawable.ic_status_wifi_4 else R.drawable.ic_status_wifi_off

internal fun bellStatusIcon(count: Int): Int = when {
    count <= 0 -> R.drawable.ic_status_bell_dot
    count == 1 -> R.drawable.ic_status_bell_1
    count == 2 -> R.drawable.ic_status_bell_2
    count == 3 -> R.drawable.ic_status_bell_3
    count == 4 -> R.drawable.ic_status_bell_4
    count == 5 -> R.drawable.ic_status_bell_5
    count == 6 -> R.drawable.ic_status_bell_6
    count == 7 -> R.drawable.ic_status_bell_7
    count == 8 -> R.drawable.ic_status_bell_8
    else -> R.drawable.ic_status_bell_9plus
}

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
    BasicText(
        text = status.time,
        style = TextStyle(color = theme.onBackground, fontSize = TypeRamp.hint, fontFamily = theme.font),
    )
    Glyph(batteryStatusIcon(status.batteryPercent, status.charging), null)
    Glyph(wifiStatusIcon(status.network), null)
    Glyph(bellStatusIcon(app.shell.model.notices.size), null)
}

@Composable
private fun Modifier.islandPress(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .focusProperties { canFocus = false }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

private fun lerp(start: Dp, end: Dp, fraction: Float): Dp = start + (end - start) * fraction
