package com.aarush.cpm.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarush.cpm.data.repository.SampleDataSeeder
import kotlinx.coroutines.launch

class SettingsViewModel(private val sampleDataSeeder: SampleDataSeeder) : ViewModel() {
    var isSeeding by mutableStateOf(false)
        private set

    fun loadDemoProject(onDone: () -> Unit) {
        viewModelScope.launch {
            isSeeding = true
            sampleDataSeeder.seedDemoProject()
            isSeeding = false
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("Defaults", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Thumb-rule percentages, material/labour splits, coefficients and waste % are all " +
                    "editable per-project from the project's cost distribution and BOQ screens.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))
            Text("Demo data", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { viewModel.loadDemoProject {} }, enabled = !viewModel.isSeeding) {
                Text(if (viewModel.isSeeding) "Loading…" else "Load sample demo project")
            }
            Spacer(Modifier.height(24.dp))
            Text("About", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Aarush Construction Project Management System — v1.0.0", style = MaterialTheme.typography.bodySmall)
            Text("Offline-first. All data stored locally on this device.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
