package net.sagberg.kartoffel.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Theme, Coverage Outline and Fog detail choices. Each choice applies and is kept as soon as it is made. */
@Composable
internal fun AppearanceDialog(
    appearance: Appearance,
    onAppearanceChange: (Appearance) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Appearance") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ChoiceGroup(
                    title = "Theme",
                    choices = ThemeMode.entries,
                    selected = appearance.themeMode,
                    label = ThemeMode::label,
                    onSelect = { onAppearanceChange(appearance.copy(themeMode = it)) },
                )
                ChoiceGroup(
                    title = "Coverage outline",
                    choices = CoverageOutlineStyle.entries,
                    selected = appearance.coverageOutline,
                    label = CoverageOutlineStyle::label,
                    onSelect = { onAppearanceChange(appearance.copy(coverageOutline = it)) },
                )
                ChoiceGroup(
                    title = "Fog detail",
                    choices = FogDetail.entries,
                    selected = appearance.fogDetail,
                    label = FogDetail::label,
                    onSelect = { onAppearanceChange(appearance.copy(fogDetail = it)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun <T> ChoiceGroup(
    title: String,
    choices: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Column(Modifier.selectableGroup()) {
            choices.forEach { choice ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(
                            selected = choice == selected,
                            onClick = { onSelect(choice) },
                            role = Role.RadioButton,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = choice == selected, onClick = null)
                    Text(
                        text = label(choice),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}
