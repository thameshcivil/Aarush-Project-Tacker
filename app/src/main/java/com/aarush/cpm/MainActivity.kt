package com.aarush.cpm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.aarush.cpm.di.AppContainer
import com.aarush.cpm.ui.navigation.AarushNavGraph
import com.aarush.cpm.ui.theme.AarushCPMTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = AppContainer(applicationContext)

        setContent {
            AarushCPMTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AarushNavGraph(appContainer = appContainer)
                }
            }
        }
    }
}
