package app.foldcade.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeProfile : ThorShell() {
    @get:Rule val profile = BaselineProfileRule()

    @Test fun firstMinute() = profile.collect(
        packageName = PACKAGE,
        maxIterations = 10,
        stableIterations = 3,
        filterPredicate = { "Lapp/foldcade/" in it },
    ) {
        startHome()
        if (iteration == 1) HomeJourney(device).key("BUTTON_B") // fresh install: Home explainer only
        HomeJourney(device).firstMinute()
    }
}
