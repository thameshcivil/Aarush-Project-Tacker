package com.aarush.cpm.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.Density
import com.aarush.cpm.data.entity.CostAllocation
import com.aarush.cpm.data.entity.CostCategory
import com.aarush.cpm.data.entity.Project
import com.aarush.cpm.data.repository.BudgetComparison
import com.aarush.cpm.data.repository.CategoryBudgetVsActual
import com.aarush.cpm.data.repository.ProjectSummary
import com.aarush.cpm.ui.project.BudgetComparisonTable
import com.aarush.cpm.ui.project.CategoryBudgetTable
import com.aarush.cpm.ui.project.MoneySummaryTable
import com.aarush.cpm.ui.project.ProjectValueHeader
import com.aarush.cpm.ui.project.TimeProgressTable
import com.aarush.cpm.ui.theme.AarushCPMTheme
import org.junit.Rule
import org.junit.Test

/*
 * Review screenshots of the Budget Tracking screen (redesign Phase 2). Not assertions: run
 * `./gradlew recordPaparazziDebug` and look at app/src/test/snapshots/images.
 *
 * Figures are the built-in DEMO project's, exactly as ProjectSummaryRepository /
 * buildBudgetComparison compute them after SampleDataSeeder.seedDemoProject(). The
 * "over budget" shot changes one category's actual to show the red state (illustration only).
 */
internal object DemoBudget {
    val project = Project(
        id = 1, name = "Residential Building - Demo", clientName = "Demo Client", clientContact = "",
        location = "Sample Layout, Demo City", projectType = "G+1", numberOfFloors = 2,
        plinthAreaSqft = 2000.0, ratePerSqft = 2500.0, projectValue = 5_000_000.0, startDate = 0, expectedCompletionDate = 0
    )
    private fun cat(c: CostCategory, pct: Double, budget: Double, actual: Double) = CategoryBudgetVsActual(
        CostAllocation(projectId = 1, category = c, percentOfProjectValue = pct), budget, actual,
        (budget - actual).coerceAtLeast(0.0), budget - actual
    )
    val categories = listOf(
        cat(CostCategory.CIVIL_STRUCTURAL, 45.0, 2_250_000.0, 684_300.0),
        cat(CostCategory.MEP, 15.0, 750_000.0, 0.0),
        cat(CostCategory.PAINTING, 8.0, 400_000.0, 0.0),
        cat(CostCategory.JOINERY, 12.0, 600_000.0, 0.0),
        cat(CostCategory.TILING, 8.0, 400_000.0, 0.0),
        cat(CostCategory.OTHER_MISC, 12.0, 600_000.0, 0.0)
    )
    val summary = ProjectSummary(project, 1_000_000.0, 684_300.0, 4_000_000.0, 315_700.0, 4_315_700.0, 86.314, 9.686, categories)
    val comparison = BudgetComparison(5_000_000.0, 1_306_355.51, 900_000.0, 2_206_355.51, 2_750_000.0, 4_956_355.51, 684_300.0, 4_272_055.51, 4_315_700.0)
    /** Illustration of the over-budget state: MEP actual raised above its budget. */
    val overBudgetCategories = categories.map {
        if (it.allocation.category == CostCategory.MEP) it.copy(actual = 812_450.0, remaining = 0.0, variance = 750_000.0 - 812_450.0) else it
    }
}

/** Which part of the screen to render: Paparazzi scales tall images down to 1000px, so the
 *  screen is captured in three sharp parts (top to bottom) instead of one blurry strip. */
internal enum class Part { TOP, MIDDLE, BOTTOM }

@Composable
private fun BudgetScreen(part: Part, dark: Boolean, categories: List<CategoryBudgetVsActual> = DemoBudget.categories) {
    AarushCPMTheme(darkTheme = dark) {
        // Surface supplies the screen's content colour, as the app's Scaffold does.
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (part) {
                    Part.TOP -> {
                        ProjectValueHeader(DemoBudget.project, onEditAreaRate = {})
                        MoneySummaryTable(DemoBudget.summary)
                    }
                    Part.MIDDLE -> {
                        TimeProgressTable(DemoBudget.summary.progressPercent, daysRemaining = 268, hasSchedule = true)
                        BudgetComparisonTable(DemoBudget.comparison)
                    }
                    Part.BOTTOM -> CategoryBudgetTable(categories)
                }
            }
        }
    }
}

/** 2x density so each part stays under Paparazzi's 1000px cap at full sharpness. */
private fun phone(widthDp: Int) = DeviceConfig.PIXEL_5.copy(
    screenWidth = widthDp * 2, screenHeight = 1600, xdpi = 320, ydpi = 320, density = Density.XHIGH
)

class BudgetTrackingScreenshots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = phone(393), theme = "android:Theme.Material.Light.NoActionBar",
        renderingMode = SessionParams.RenderingMode.SHRINK)

    @Test fun light_1_top() = paparazzi.snapshot { BudgetScreen(Part.TOP, dark = false) }
    @Test fun light_2_middle() = paparazzi.snapshot { BudgetScreen(Part.MIDDLE, dark = false) }
    @Test fun light_3_categories() = paparazzi.snapshot { BudgetScreen(Part.BOTTOM, dark = false) }
    @Test fun dark_1_top() = paparazzi.snapshot { BudgetScreen(Part.TOP, dark = true) }
    @Test fun dark_3_categories() = paparazzi.snapshot { BudgetScreen(Part.BOTTOM, dark = true) }
}

/** 320dp wide: the narrowest common Android phone width. */
class BudgetTrackingNarrowScreenshots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = phone(320), theme = "android:Theme.Material.Light.NoActionBar",
        renderingMode = SessionParams.RenderingMode.SHRINK)

    @Test fun narrow320_1_top() = paparazzi.snapshot { BudgetScreen(Part.TOP, dark = false) }
    @Test fun narrow320_3_categories_over_budget() =
        paparazzi.snapshot { BudgetScreen(Part.BOTTOM, dark = false, categories = DemoBudget.overBudgetCategories) }
}
