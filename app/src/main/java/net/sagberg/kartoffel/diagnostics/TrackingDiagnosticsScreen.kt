package net.sagberg.kartoffel.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.sagberg.kartoffel.storage.KartoffelDatabase
import net.sagberg.kartoffel.storage.PersistedActivityMode
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun TrackingDiagnosticsRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember(context) { KartoffelDatabase.open(context) }
    var loading by remember { mutableStateOf(true) }
    var history by remember { mutableStateOf<RecordingSessionHistory?>(null) }

    LaunchedEffect(database) {
        val loader = RecordingSessionHistoryLoader(database)
        do {
            history = loader.load(System.currentTimeMillis())
            loading = false
            if (history?.isActive == true) delay(1.seconds)
        } while (history?.isActive == true)
    }

    TrackingDiagnosticsScreen(history = history, loading = loading, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackingDiagnosticsScreen(
    history: RecordingSessionHistory?,
    loading: Boolean = false,
    onBack: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Tracking diagnostics") },
                navigationIcon = {
                    TextButton(
                        modifier = Modifier.testTag("tracking_diagnostics_back"),
                        onClick = onBack,
                    ) { Text("Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                loading -> Text("Loading Recording Session…")
                history == null -> {
                    Text("No Recording Sessions yet", style = MaterialTheme.typography.titleMedium)
                    Text("Start recording from the map to see a session summary here.")
                }
                else -> SessionHistory(history)
            }
        }
    }
}

@Composable
private fun SessionHistory(history: RecordingSessionHistory) {
    Text(
        if (history.isActive) "Active Recording Session" else "Latest Recording Session",
        style = MaterialTheme.typography.titleMedium,
    )
    Text("Duration: ${history.durationMillis.displayDuration()}")
    HorizontalDivider()
    Text("Fix counts by Activity Mode", style = MaterialTheme.typography.titleSmall)
    if (history.fixCounts.values.sum() == 0) {
        Text("No fixes recorded yet")
    } else {
        PersistedActivityMode.entries.forEach { mode ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(modifier = Modifier.weight(1f), text = mode.displayName)
                Text((history.fixCounts[mode] ?: 0).toString())
            }
        }
        Text(
            "Counts are location fixes, not time spent.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val PersistedActivityMode.displayName: String
    get() = when (this) {
        PersistedActivityMode.STILL -> "Still"
        PersistedActivityMode.WALKING -> "Walking"
        PersistedActivityMode.RUNNING -> "Running"
        PersistedActivityMode.CYCLING -> "Cycling"
        PersistedActivityMode.IN_VEHICLE -> "In vehicle"
        PersistedActivityMode.UNKNOWN -> "Unknown"
    }

internal fun Long.displayDuration(): String {
    val totalSeconds = coerceAtLeast(0) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
