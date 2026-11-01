package app.foldcade.baselineprofile

import androidx.benchmark.ExperimentalBenchmarkConfigApi
import androidx.benchmark.ExperimentalConfig
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingGfxInfoMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.perfetto.ExperimentalPerfettoCaptureApi
import androidx.benchmark.perfetto.PerfettoConfig
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Firmware 377's traced_probes crashes on linux.ftrace. Honest, named gfxinfo fallback. */
@OptIn(ExperimentalMetricApi::class, ExperimentalBenchmarkConfigApi::class, ExperimentalPerfettoCaptureApi::class)
@RunWith(AndroidJUnit4::class)
class HomeGfxBenchmarks : ThorShell() {
    @get:Rule val benchmark = MacrobenchmarkRule()
    @Test fun gridNone() = grid(CompilationMode.None())
    @Test fun gridBaseline() = grid(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun grid(compilation: CompilationMode) = benchmark.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingGfxInfoMetric()),
        compilationMode = compilation,
        iterations = 5,
        experimentalConfig = ExperimentalConfig(perfettoConfig = PerfettoConfig.Text("""
            buffers { size_kb: 16384 fill_policy: RING_BUFFER }
            data_sources { config { name: "android.surfaceflinger.frametimeline" } }
        """.trimIndent())),
        setupBlock = {
            startHome()
            if (iteration == 0) HomeJourney(device).key("BUTTON_B") // fresh Home explainer
        },
    ) { HomeJourney(device).grid() }
}
