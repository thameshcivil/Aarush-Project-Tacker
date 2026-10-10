package com.aarush.cpm.ui.boq

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.aarush.cpm.ui.theme.AarushCPMTheme
import org.junit.Rule
import org.junit.Test

/*
 * Review screenshots of the BOQ measurement sheet (redesign Phase 3). Not assertions: run
 * `./gradlew recordPaparazziDebug` and look at app/src/test/snapshots/images.
 * Data: the built-in demo project's BOQ (DemoBoq), unchanged. The "RCC + brickwork" shots show
 * just those two notations so the expanded rows fit on one screen.
 */
private const val RCC = "003-Reinforced Cement Concrete"
private val rccAndBrick = DemoBoq.items.filter { it.itemCode == RCC || it.itemCode == "004-9\" Brick work" }

@Composable
private fun Sheet(dark: Boolean, items: List<com.aarush.cpm.data.entity.BOQItem>, mode: BOQSortMode,
                  expandedMain: Set<String>, expandedSub: Set<String>, copied: Boolean) {
    AarushCPMTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            BOQSheet(
                items = items, coefficients = DemoBoq.coefficients, floorOrder = DemoBoq.floorOrder,
                sortMode = mode, onSortMode = {}, expandedMain = expandedMain, onToggleMain = {},
                expandedSub = expandedSub, onToggleSub = {},
                copiedItem = if (copied) items.first { it.description == "Columns" } else null,
                onCopy = {}, onPaste = {}, onClearCopy = {}, onEdit = {}, onDelete = {}, onAdd = {}
            )
        }
    }
}

private val rccOpen = setOf(RCC)
private val rccSubsOpen = setOf("$RCC|Columns", "$RCC|Beams & slab", "$RCC|Isolated footings")

/** 1.5x density, 1000px tall: one phone screen, under Paparazzi's 1000px cap. */
private fun phone(widthDp: Int) = DeviceConfig.PIXEL_5.copy(
    screenWidth = widthDp * 3 / 2, screenHeight = 1000, xdpi = 240, ydpi = 240, density = Density.HIGH
)

class BOQSheetScreenshots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = phone(393), theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun boq_1_expanded_with_copied_bar() =
        paparazzi.snapshot { Sheet(false, rccAndBrick, BOQSortMode.NOTATIONS, rccOpen, rccSubsOpen, copied = true) }
    @Test fun boq_2_all_groups_collapsed() =
        paparazzi.snapshot { Sheet(false, DemoBoq.items, BOQSortMode.NOTATIONS, emptySet(), emptySet(), copied = false) }
    @Test fun boq_3_group_by_description() =
        paparazzi.snapshot { Sheet(false, DemoBoq.items, BOQSortMode.DESCRIPTION, setOf("Columns"), setOf("Columns|$RCC"), copied = false) }
    @Test fun boq_4_dark_expanded() =
        paparazzi.snapshot { Sheet(true, rccAndBrick, BOQSortMode.NOTATIONS, rccOpen, rccSubsOpen, copied = false) }
}

/** 320dp: narrowest common phone. Rows switch to the two-line layout; Qty stays on screen. */
class BOQSheetNarrowScreenshots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = phone(320), theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun boq_narrow320_expanded_with_copied_bar() =
        paparazzi.snapshot { Sheet(false, rccAndBrick, BOQSortMode.NOTATIONS, rccOpen, rccSubsOpen, copied = true) }
}

/** 360dp: the most common Android width. Still wide enough (>= 355dp) for the one-line table. */
class BOQSheet360Screenshots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = phone(360), theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun boq_360_expanded() =
        paparazzi.snapshot { Sheet(false, rccAndBrick, BOQSortMode.NOTATIONS, rccOpen, rccSubsOpen, copied = false) }
}
