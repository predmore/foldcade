package app.foldcade

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import app.foldcade.language.*
import app.foldcade.ui.*
import java.io.File
import kotlinx.coroutines.delay

/** Author preview uses the actual theme loader and renderer. This activity is absent from release. */
class ThemePreviewActivity : ComponentActivity() {
    private var running by mutableStateOf(false)
    override fun onResume() { super.onResume(); running = true }
    override fun onPause() { running = false; super.onPause() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.windowInsetsController?.hide(android.view.WindowInsets.Type.systemBars())
        fun pack(extra: String): PackagedTheme? = intent.getStringExtra(extra)?.let { path ->
            val file = File(filesDir, path)
            if (!file.canonicalPath.startsWith(filesDir.canonicalPath + File.separator)) return@let null
            try { PackagedTheme.load(this, file.inputStream()) } catch (error: Exception) {
                Log.w("FoldcadeTheme", "$path: ${error.message}"); null
            }
        }
        val base = pack("pack") ?: PackagedTheme.load(this)
        val backgrounds = pack("backgrounds") ?: base
        val icons = pack("icons")
        val fallback = PackagedTheme.load(this)
        val composition = ThemeComposition(base?.resources, icons?.resources, fallback?.resources)
        val slots = (composition.base?.icons?.keys.orEmpty() + composition.iconProvider?.icons?.keys.orEmpty() + composition.fallback?.icons?.keys.orEmpty()).toSet()
        val paint = (base?.paint ?: FoldPaint(builtInTheme())).copy(icons = slots.associateWith(composition::iconCandidates))
        val scenes = backgrounds?.resources?.scenes?.values?.toList().orEmpty()
        val requested = intent.getStringExtra("scene")
        val panel = intent.getIntExtra("panel", 0).toFloat()
        val fixedTime = intent.getStringExtra("time")?.toFloatOrNull()
        val chrome = intent.getBooleanExtra("chrome", true)
        setContent {
            val referenceIcons = if (intent.getBooleanExtra("validateIcons", false) && base?.resources?.id == "afterglow") {
                base.resources.icons.mapValues { (_, icon) ->
                    val name = icon.file.substringAfterLast('/').substringBeforeLast('.')
                    painterResource(R.drawable::class.java.getField(name).getInt(null))
                }
            } else emptyMap()
            LaunchedEffect(base) {
                if (intent.getBooleanExtra("validateIcons", false) && base != null) validatePreviewIcons(this@ThemePreviewActivity, base, referenceIcons)
            }
            CompositionLocalProvider(LocalFoldTheme provides paint) {
                var index by remember { mutableIntStateOf(scenes.indexOfFirst { it.id == requested }.coerceAtLeast(0)) }
                var reduced by remember { mutableStateOf(false) }
                var paused by remember { mutableStateOf(false) }
                var metrics by remember { mutableStateOf<SceneMetrics?>(null) }
                var stats by remember { mutableStateOf("Preparing scene…") }
                val scene = scenes.getOrNull(index)
                LaunchedEffect(scene) {
                    var lastFrames = 0L
                    var lastCpu = 0L
                    while (true) {
                        delay(1000)
                        metrics?.let { sample ->
                            val frames = sample.frames - lastFrames
                            val cpu = sample.cpuNanos - lastCpu
                            stats = "$frames draws/s · CPU submission ${"%.2f".format(if (frames > 0) cpu / frames / 1_000_000.0 else 0.0)} ms · ${sample.layers} layers · ${sample.textureBytes / 1024} KiB textures"
                            Log.i("FoldcadeThemePreview", "${scene?.source}: $stats")
                            lastFrames = sample.frames; lastCpu = sample.cpuNanos
                        }
                    }
                }
                Box(Modifier.fillMaxSize().background(paint.theme.background)) {
                    Backdrop(scene, MotionSpeed.Slow, if (reduced) 0f else 1f, running, paused,
                        panel = panel, previewTime = fixedTime, onMetrics = { metrics = it })
                    if (chrome) Column(Modifier.padding(24.dp).background(paint.theme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        fun label() = TextStyle(color = paint.theme.onBackground, fontFamily = paint.theme.font,
                            fontSize = paint.theme.style.type.hint)
                        BasicText("${base?.name ?: "Built-in"} · ${scene?.name ?: "No scenes"}", style = label())
                        BasicText(stats, style = label())
                        BasicText("Next scene", Modifier.clickable { if (scenes.isNotEmpty()) index = (index + 1) % scenes.size }, style = label())
                        BasicText("Reduced motion: $reduced", Modifier.clickable { reduced = !reduced }, style = label())
                        BasicText("Paused: $paused", Modifier.clickable { paused = !paused }, style = label())
                        Row(Modifier.size(144.dp, 96.dp)) {
                            Box(Modifier.size(96.dp)) { MarkIcon("nintendo-3ds", paint.theme.artScale) }
                        }
                    }
                }
            }
        }
    }
}
