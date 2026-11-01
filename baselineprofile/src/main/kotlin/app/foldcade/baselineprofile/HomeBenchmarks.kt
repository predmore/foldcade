package app.foldcade.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeBenchmarks : ThorShell() {
    @get:Rule val benchmark = MacrobenchmarkRule()

    @Test fun coldNone() = cold(CompilationMode.None())
    @Test fun coldBaseline() = cold(CompilationMode.Partial(BaselineProfileMode.Require))
    @Test fun gridNone() = grid(CompilationMode.None())
    @Test fun gridBaseline() = grid(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun cold(compilation: CompilationMode) = benchmark.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilation,
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = {
            if (iteration == 0) {
                startHome()
                HomeJourney(device).key("BUTTON_B") // fresh Home explainer, outside timing
            }
        },
    ) { startHome() }

    private fun grid(compilation: CompilationMode) = benchmark.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation,
        iterations = 5,
        setupBlock = {
            startHome()
            if (iteration == 0) HomeJourney(device).key("BUTTON_B")
        },
    ) { HomeJourney(device).grid() }
}
