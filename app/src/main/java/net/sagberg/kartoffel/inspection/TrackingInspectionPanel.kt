package net.sagberg.kartoffel.inspection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrackingInspectionControls(
    filter: TrackingInspectionFilter,
    sessions: List<InspectionRecordingSession>,
    nowMillis: Long,
    loading: Boolean,
    empty: Boolean,
    onFilterChange: (TrackingInspectionFilter) -> Unit,
    manualRouteClaims: List<InspectionManualRouteClaim> = emptyList(),
    selectedManualRouteClaimId: Long? = null,
    onSelectManualRouteClaim: (Long) -> Unit = {},
    onWithdrawManualRouteClaim: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var scopeMenu by remember { mutableStateOf(false) }
    var timeMenu by remember { mutableStateOf(false) }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingWithdrawal by remember { mutableStateOf<InspectionManualRouteClaim?>(null) }
    val customTime = filter.time as? TrackingInspectionTime.Custom
    var customStart by remember(filter.time) {
        mutableStateOf(customTime?.startMillis?.localDateTime().orEmpty())
    }
    var customEnd by remember(filter.time) {
        mutableStateOf(customTime?.endMillis?.localDateTime().orEmpty())
    }
    val scopes = listOf(
        TrackingInspectionScope.AllTracking,
        TrackingInspectionScope.PassiveTracking,
        TrackingInspectionScope.AllRecordingSessions,
    ) + sessions.map { TrackingInspectionScope.RecordingSession(it.id) }
    val times = listOf(
        TrackingInspectionTime.AllTime,
        TrackingInspectionTime.Last24Hours(nowMillis),
        TrackingInspectionTime.Last7Days(nowMillis),
    )

    pendingWithdrawal?.let { claim ->
        AlertDialog(
            onDismissRequest = { pendingWithdrawal = null },
            title = { Text("Withdraw Manual Route Claim?") },
            text = { Text("Cells supported only by this claim will be fogged again.") },
            dismissButton = {
                TextButton(onClick = { pendingWithdrawal = null }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingWithdrawal = null
                    onWithdrawManualRouteClaim(claim.id)
                }) { Text("Withdraw") }
            },
        )
    }

    Card(modifier = modifier.fillMaxWidth().testTag("inspection_filter_controls")) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box {
                        FilterChip(
                            selected = filter.scope != TrackingInspectionScope.AllTracking,
                            onClick = { scopeMenu = true },
                            label = { Text(filter.scope.label(sessions)) },
                        )
                        DropdownMenu(
                            expanded = scopeMenu,
                            onDismissRequest = { scopeMenu = false },
                        ) {
                            scopes.forEach { scope ->
                                DropdownMenuItem(
                                    text = { Text(scope.label(sessions)) },
                                    onClick = {
                                        scopeMenu = false
                                        onFilterChange(filter.copy(scope = scope))
                                    },
                                )
                            }
                        }
                    }
                    Box {
                        FilterChip(
                            selected = filter.time != TrackingInspectionTime.AllTime,
                            onClick = { timeMenu = true },
                            label = { Text(filter.time.label()) },
                        )
                        DropdownMenu(
                            expanded = timeMenu,
                            onDismissRequest = { timeMenu = false },
                        ) {
                            (times + TrackingInspectionTime.Custom(
                                nowMillis - 1.hours.inWholeMilliseconds,
                                nowMillis,
                            )).forEach { time ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (time is TrackingInspectionTime.Custom) {
                                                "Custom"
                                            } else {
                                                time.label()
                                            },
                                        )
                                    },
                                    onClick = {
                                        timeMenu = false
                                        onFilterChange(filter.copy(time = time))
                                        if (time is TrackingInspectionTime.Custom) {
                                            filtersExpanded = true
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = { filtersExpanded = !filtersExpanded }) {
                    Text(if (filtersExpanded) "Done" else "Filters")
                }
            }

            if (filtersExpanded) {
                HorizontalDivider()
                FilterGroup(
                    label = "Scope",
                    choices = scopes,
                    selected = filter.scope,
                    choiceLabel = { it.label(sessions) },
                    onChange = { onFilterChange(filter.copy(scope = it)) },
                )
                FilterGroup(
                    label = "Time",
                    choices = times,
                    selected = filter.time,
                    choiceLabel = TrackingInspectionTime::label,
                    onChange = { onFilterChange(filter.copy(time = it)) },
                )
                FilterChip(
                    selected = customTime != null,
                    onClick = {
                        onFilterChange(
                            filter.copy(
                                time = TrackingInspectionTime.Custom(
                                    nowMillis - 1.hours.inWholeMilliseconds,
                                    nowMillis,
                                ),
                            ),
                        )
                    },
                    label = { Text("Custom") },
                )
                if (customTime != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = customStart,
                            onValueChange = { customStart = it },
                            label = { Text("From") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = customEnd,
                            onValueChange = { customEnd = it },
                            label = { Text("To") },
                            singleLine = true,
                        )
                    }
                    TextButton(onClick = {
                        val start = customStart.toEpochMillisOrNull()
                        val end = customEnd.toEpochMillisOrNull()
                        if (start != null && end != null && start < end) {
                            onFilterChange(
                                filter.copy(time = TrackingInspectionTime.Custom(start, end)),
                            )
                        }
                    }) { Text("Apply custom time") }
                }
            }

            InspectionLegend()
            if (manualRouteClaims.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .testTag("manual_route_claim_list"),
                ) {
                    itemsIndexed(manualRouteClaims, key = { _, claim -> claim.id }) { index, claim ->
                        if (index > 0) HorizontalDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .testTag("manual_route_claim_${claim.id}"),
                        ) {
                            Text(
                                "Manual Route Claim · ${claim.createdAtMillis.compactTime()}",
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = { onSelectManualRouteClaim(claim.id) }) {
                                    Text(
                                        if (selectedManualRouteClaimId == claim.id) {
                                            "Previewing"
                                        } else {
                                            "Preview"
                                        },
                                        maxLines = 1,
                                    )
                                }
                                TextButton(
                                    modifier = Modifier.testTag(
                                        "withdraw_manual_route_claim_${claim.id}",
                                    ),
                                    onClick = { pendingWithdrawal = claim },
                                ) {
                                    Text("Withdraw", maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            when {
                loading -> Text(
                    "Loading retained evidence…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                empty -> Text(
                    "No retained evidence matches these filters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun <T> FilterGroup(
    label: String,
    choices: List<T>,
    selected: T,
    choiceLabel: (T) -> String,
    onChange: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(choices) { choice ->
                FilterChip(
                    selected = choice == selected,
                    onClick = { onChange(choice) },
                    label = { Text(choiceLabel(choice)) },
                )
            }
        }
    }
}

@Composable
private fun InspectionLegend() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(DiagnosticCellState.entries) { state ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(state.color, RoundedCornerShape(50)),
                )
                Text(state.label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
internal fun TrackingInspectionDetails(
    cell: DiagnosticSampleCell,
    sort: SampleSort,
    onSortChange: (SampleSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var historyExpanded by rememberSaveable(cell.cellId) { mutableStateOf(false) }

    Card(modifier = modifier.testTag("inspection_cell_details")) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                cell.state.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Diagnostic Sample Cell …${cell.cellId.toString(16).takeLast(6)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${cell.samples.size} total · ${cell.acceptedCount} accepted · " +
                    "${cell.rejectedCount} rejected · ${cell.provenance.displayName}",
            )
            Text("${cell.coverageEvidenceLabel} · ${cell.displayTimeSpan}")
            HorizontalDivider()

            if (cell.samples.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { historyExpanded = !historyExpanded }) {
                        Text(if (historyExpanded) "Hide samples" else "Samples (${cell.samples.size})")
                    }
                    if (historyExpanded) {
                        TextButton(
                            modifier = Modifier.testTag("inspection_sort"),
                            onClick = {
                                onSortChange(
                                    if (sort == SampleSort.NewestFirst) {
                                        SampleSort.OldestFirst
                                    } else {
                                        SampleSort.NewestFirst
                                    },
                                )
                            },
                        ) {
                            Text(
                                if (sort == SampleSort.NewestFirst) {
                                    "Newest first"
                                } else {
                                    "Oldest first"
                                },
                            )
                        }
                    }
                }
                if (historyExpanded) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .testTag("sample_history"),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(cell.samples(sort), key = DiagnosticSample::id) { sample ->
                            Text(
                                sample.displayName,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            } else {
                Text(
                    "Inferred Coverage Evidence; no Location Samples",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

internal val DiagnosticCellState.color: Color
    get() = when (this) {
        DiagnosticCellState.Observed -> Color(0xFF147D3F)
        DiagnosticCellState.Inferred -> Color(0xFF5B4ACB)
        DiagnosticCellState.RejectedOnly -> Color(0xFFB3261E)
    }

private val localDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val compactTimeFormatter = DateTimeFormatter.ofPattern("MMM d · HH:mm")

private fun Long.localDateTime(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(localDateTimeFormatter)

private fun Long.compactTime(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(compactTimeFormatter)

private fun String.toEpochMillisOrNull(): Long? = runCatching {
    LocalDateTime.parse(this, localDateTimeFormatter)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}.getOrNull()

private fun TrackingInspectionScope.label(sessions: List<InspectionRecordingSession>): String =
    when (this) {
        TrackingInspectionScope.AllTracking -> "All tracking"
        TrackingInspectionScope.PassiveTracking -> "Passive"
        TrackingInspectionScope.AllRecordingSessions -> "All Recording Sessions"
        is TrackingInspectionScope.RecordingSession ->
            sessions.firstOrNull { it.id == id }?.displayName ?: "Recording Session"
    }

private val InspectionRecordingSession.displayName: String
    get() {
        val duration = endedAtMillis?.let { ((it - startedAtMillis) / 60_000).coerceAtLeast(0) }
        return buildString {
            append(startedAtMillis.compactTime())
            append(" · ")
            append(duration?.let { "$it min" } ?: "Active")
        }
    }

private fun TrackingInspectionTime.label(): String = when (this) {
    TrackingInspectionTime.AllTime -> "All time"
    is TrackingInspectionTime.Last24Hours -> "24 hours"
    is TrackingInspectionTime.Last7Days -> "7 days"
    is TrackingInspectionTime.Custom -> "Custom"
}

private val DiagnosticCellState.label: String
    get() = when (this) {
        DiagnosticCellState.Observed -> "Observed"
        DiagnosticCellState.Inferred -> "Inferred"
        DiagnosticCellState.RejectedOnly -> "Rejected-only"
    }

private val Set<DiagnosticProvenance>.displayName: String
    get() {
        val passive = any { it.name.startsWith("Passive") }
        val recording = any { it.name.startsWith("Recording") }
        return when {
            passive && recording -> "Passive + Recording Session"
            passive -> "Passive"
            recording -> "Recording Session"
            else -> "No source provenance"
        }
    }

private val DiagnosticSampleCell.coverageEvidenceLabel: String
    get() {
        val observed = provenance.any {
            it == DiagnosticProvenance.PassiveObserved ||
                it == DiagnosticProvenance.RecordingObserved
        }
        val inferred = provenance.any {
            it == DiagnosticProvenance.PassiveInferred ||
                it == DiagnosticProvenance.RecordingInferred
        }
        return when {
            observed && inferred -> "Observed + Inferred Coverage Evidence"
            observed -> "Observed Coverage Evidence"
            inferred -> "Inferred Coverage Evidence"
            else -> "No Coverage Evidence"
        }
    }

private val DiagnosticSampleCell.displayTimeSpan: String
    get() {
        val first = evidenceFirstMillis ?: sampleFirstMillis
        val last = evidenceLastMillis ?: sampleLastMillis
        return when {
            first == null -> "No matching time span"
            last == null || first == last -> first.compactTime()
            else -> "${first.compactTime()}–${last.compactTime()}"
        }
    }

private val DiagnosticSample.displayName: String
    get() = listOfNotNull(
        capturedAtMillis.compactTime(),
        if (accepted) "Accepted" else "Rejected",
        "${accuracyMeters.toInt()} m",
        recordingSessionId?.let { "Recording Session" } ?: "Passive",
        recordingSessionId?.let { "Session $it" } ?: trigger?.replace('_', ' '),
        activityMode.replace('_', ' ').replaceFirstChar(Char::uppercase),
    ).joinToString(" · ")
