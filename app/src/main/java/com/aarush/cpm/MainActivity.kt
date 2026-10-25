package com.aarush.cpm

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.aarush.cpm.di.AppContainer
import com.aarush.cpm.ui.navigation.AarushNavGraph
import com.aarush.cpm.ui.theme.AarushCPMTheme

// FragmentActivity (a ComponentActivity subclass) instead of plain ComponentActivity —
// BiometricPrompt requires a FragmentActivity/FragmentManager. Everything Compose-related
// (setContent, enableEdgeToEdge) still works unchanged since FragmentActivity inherits it.
class MainActivity : FragmentActivity() {
    private var container: AppContainer? = null

    /** Coming back to the app: pick up what other phones changed meanwhile. */
    override fun onResume() {
        super.onResume()
        container?.let { c -> lifecycleScope.launch { c.cloudSync.syncNow() } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = AppContainer.get(applicationContext)
        container = appContainer
        // Join the team table (first phone on v10 becomes Owner; new phones join as Viewer),
        // then resume cloud sync if it is on.
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                runCatching { appContainer.accessRepository.registerThisDevice() }
                appContainer.cloudSync.start()
            }
        }

        setContent {
            AarushCPMTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AarushNavGraph(appContainer = appContainer)
                }
            }
        }
    }
}
