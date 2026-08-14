package net.sagberg.kartoffel.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import net.sagberg.kartoffel.storage.KartoffelDatabase
import kotlin.math.roundToInt

@Composable
internal fun CoverageSettingsRoute(
    persistenceScope: CoroutineScope,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val database = remember(context) { KartoffelDatabase.open(context) }
    val repository = remember(database) { database.coverageSettingsRepository }
    val settings by repository.observe().collectAsState(initial = CoverageSettings.Default)

    CoverageSettingsScreen(
        settings = settings,
        onMaximumAcceptedAccuracyChangeFinished = { value ->
            persistenceScope.launch(start = CoroutineStart.UNDISPATCHED) {
                repository.setMaximumAcceptedAccuracyMeters(value)
            }
        },
        onMaximumInterpolationGapChangeFinished = { value ->
            persistenceScope.launch(start = CoroutineStart.UNDISPATCHED) {
                repository.setMaximumInterpolationGapSteps(value)
            }
        },
        onReset = {
            persistenceScope.launch(start = CoroutineStart.UNDISPATCHED) { repository.reset() }
        },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoverageSettingsScreen(
    settings: CoverageSettings,
    onMaximumAcceptedAccuracyChangeFinished: (Int) -> Unit,
    onMaximumInterpolationGapChangeFinished: (Int) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    var accuracyMeters by remember(settings.maximumAcceptedAccuracyMeters) {
        mutableFloatStateOf(settings.maximumAcceptedAccuracyMeters.toFloat())
    }
    var interpolationSteps by remember(settings.maximumInterpolationGapSteps) {
        mutableFloatStateOf(settings.maximumInterpolationGapSteps.toFloat())
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(
                        modifier = Modifier.testTag("coverage_settings_back"),
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            CoverageSettingSlider(
                title = "Maximum accepted accuracy",
                valueLabel = "${accuracyMeters.roundToInt()} m",
                supportingText = "Fixes with a larger accuracy radius do not clear coverage.",
                value = accuracyMeters,
                onValueChange = { accuracyMeters = it },
                onValueChangeFinished = {
                    onMaximumAcceptedAccuracyChangeFinished(accuracyMeters.roundToInt())
                },
                valueRange = CoverageSettings.ACCEPTED_ACCURACY_RANGE.first.toFloat()..
                    CoverageSettings.ACCEPTED_ACCURACY_RANGE.last.toFloat(),
                steps = 5,
                testTag = "maximum_accuracy_slider",
            )
            CoverageSettingSlider(
                title = "Maximum interpolation gap",
                valueLabel = interpolationSteps.roundToInt().let { value ->
                    "$value ${if (value == 1) "step" else "steps"}"
                },
                supportingText = "Fills one shortest path between accepted fixes. " +
                    "One step disables interpolation.",
                value = interpolationSteps,
                onValueChange = { interpolationSteps = it },
                onValueChangeFinished = {
                    onMaximumInterpolationGapChangeFinished(interpolationSteps.roundToInt())
                },
                valueRange = CoverageSettings.INTERPOLATION_GAP_RANGE.first.toFloat()..
                    CoverageSettings.INTERPOLATION_GAP_RANGE.last.toFloat(),
                steps = 8,
                testTag = "maximum_interpolation_gap_slider",
            )
            TextButton(onClick = onReset) {
                Text("Reset to defaults")
            }
        }
    }
}

@Composable
private fun CoverageSettingSlider(
    title: String,
    valueLabel: String,
    supportingText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    testTag: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                modifier = Modifier.weight(1f),
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(valueLabel, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = supportingText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
        )
    }
}
