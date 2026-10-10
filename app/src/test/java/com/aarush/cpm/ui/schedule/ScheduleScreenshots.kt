package com.aarush.cpm.ui.schedule

import androidx.compose.foundation.layout.Column
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
 * Review screenshots of the Schedule screen (redesign Phase 4). Not assertions: run
 * `./gradlew recordPaparazziDebug` and look at app/src/test/snapshots/images.
 * Data: the demo project's real schedule report (DemoSchedule, generated).
 */
private val floors = listOf("Foundation", "Plinth", "Ground Floor", "First Floor", "Terrace")

@Composable
private fun Screen(dark: Boolean, section: ScheduleSection) {
    AarushCPMTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                ScheduleTopRow(section, {}, true, {}, {}, {}, {}, {})
                when (section) {
                    ScheduleSection.OVERVIEW -> ScheduleOverview(DemoSchedule.report, onOpenActivity = {})
                    ScheduleSection.ACTIVITIES -> ScheduleActivitiesList(DemoSchedule.report, floors, onOpen = {})
                    ScheduleSection.GANTT -> ScheduleGantt(DemoSchedule.report, onOpen = {})
                    ScheduleSection.LOG -> ScheduleSiteLog(DemoSchedule.report, onDeleteLog = {})
                }
            }
        }
    }
}

/** 1.5x density so one phone screen fits under Paparazzi's 1000px cap at full sharpness. */
private fun phone(widthDp: Int, heightPx: Int = 1000) = DeviceConfig.PIXEL_5.copy(
    screenWidth = widthDp * 3 / 2, screenHeight = heightPx, xdpi = 240, ydpi = 240, density = Density.HIGH
)

class ScheduleScreenshots {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone(393), theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun schedule_1_overview() = paparazzi.snapshot { Screen(false, ScheduleSection.OVERVIEW) }
    @Test fun schedule_3_activities_by_floor() = paparazzi.snapshot { Screen(false, ScheduleSection.ACTIVITIES) }
    @Test fun schedule_4_gantt() = paparazzi.snapshot { Screen(false, ScheduleSection.GANTT) }
    @Test fun schedule_6_site_log() = paparazzi.snapshot { Screen(false, ScheduleSection.LOG) }
    @Test fun schedule_7_dark_gantt() = paparazzi.snapshot { Screen(true, ScheduleSection.GANTT) }
}

/** A taller render of the overview (scaled down) to show what sits below the key figures. */
class ScheduleOverviewTallScreenshots {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone(393, heightPx = 2400), theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun schedule_2_overview_full() = paparazzi.snapshot { Screen(false, ScheduleSection.OVERVIEW) }
}

class ScheduleNarrowScreenshots {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = phone(320), theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun schedule_narrow320_overview() = paparazzi.snapshot { Screen(false, ScheduleSection.OVERVIEW) }
    @Test fun schedule_narrow320_gantt() = paparazzi.snapshot { Screen(false, ScheduleSection.GANTT) }
}
