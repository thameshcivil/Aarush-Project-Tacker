package com.aarush.cpm.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.aarush.cpm.data.update.UpdateInfo
import com.aarush.cpm.data.update.UpdateState
import com.aarush.cpm.ui.theme.AarushCPMTheme
import org.junit.Rule
import org.junit.Test

/* Review screenshots: the in-app "Update available" card in its three states. */
class UpdateBannerScreenshots {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 590, screenHeight = 1300, xdpi = 240, ydpi = 240, density = Density.HIGH),
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    private val info = UpdateInfo(142, "1.2.42", "Cloud sync between the team's phones (Phase 2)", "")

    @Test fun update_banner_states() = paparazzi.snapshot {
        AarushCPMTheme(darkTheme = false) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    UpdateBannerContent(UpdateState.Available(info))
                    UpdateBannerContent(UpdateState.Downloading(info, 64))
                    UpdateBannerContent(UpdateState.NeedsPermission(info))
                }
            }
        }
    }
}
