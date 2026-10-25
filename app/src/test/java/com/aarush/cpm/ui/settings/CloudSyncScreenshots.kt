package com.aarush.cpm.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.aarush.cpm.data.sync.SyncStatus
import com.aarush.cpm.ui.theme.AarushCPMTheme
import org.junit.Rule
import org.junit.Test

/* Review screenshots: Settings > Cloud sync, off (Owner) and on with changes waiting. */

@Composable
private fun States() {
    AarushCPMTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                CloudSyncSection(SyncStatus(enabled = false), working = false, canStartCloud = true, onTurnOn = {}, onSyncNow = {}, onTurnOff = {})
                Spacer(Modifier.height(24.dp))
                CloudSyncSection(
                    SyncStatus(enabled = true, lastSyncAt = System.currentTimeMillis() - 4 * 60_000, pending = 3),
                    working = false, canStartCloud = true, onTurnOn = {}, onSyncNow = {}, onTurnOff = {}
                )
                Spacer(Modifier.height(24.dp))
                CloudSyncSection(
                    SyncStatus(enabled = true, lastSyncAt = System.currentTimeMillis() - 3 * 3_600_000, pending = 12,
                        error = "Offline. Changes are saved on this phone and will sync when you're back online."),
                    working = false, canStartCloud = false, onTurnOn = {}, onSyncNow = {}, onTurnOff = {}
                )
            }
        }
    }
}

class CloudSyncScreenshots {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 590, screenHeight = 1500, xdpi = 240, ydpi = 240, density = Density.HIGH),
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Test fun cloud_sync_states() = paparazzi.snapshot { States() }
}
