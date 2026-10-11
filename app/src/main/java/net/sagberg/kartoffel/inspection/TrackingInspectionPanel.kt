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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
    nowMillis: Long,
    loading: Boolean,
    empty: Boolean,
    onFilterChange: (TrackingInspectionFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var timeMenu by remember { mutableStateOf(false) }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    val customTime = filter.time as? TrackingInspectionTime.Custom
    var customStart by remember(filter.time) {
        mutableStateOf(customTime?.startMillis?.localDateTime().orEmpty())
    }
    var customEnd by remember(filter.time) {
        mutableStateOf(customTime?.endMillis?.localDateTime().orEmpty())
    }
    val times = listOf(
        TrackingInspectionTime.AllTime,
        TrackingInspectionTime.Last24Hours(nowMillis),
        TrackingInspectionTime.Last7Days(nowMillis),
    )

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
                    InspectionSource.entries.forEach { source ->
                        FilterChip(
                            selected = source in filter.sources,
                            onClick = {
                                val sources = if (source in filter.sources) {
                                    filter.sources - source
                                } else {
                                    filter.sources + source
                                }
                                onFilterChange(filter.copy(sources = sources))
                            },
                            label = { Text(source.label) },
                        )
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

private enum class InspectionTab { Info, Samples, Routes }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackingInspectionDetails(
    cell: DiagnosticSampleCell?,
    sort: SampleSort,
    onSortChange: (SampleSort) -> Unit,
    cellId: Long,
    drawnRoutes: List<InspectionDrawnRoute> = emptyList(),
    onRemoveDrawnRoute: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable(cellId) { mutableStateOf(InspectionTab.Info) }
    var pendingRemoval by remember(cellId) { mutableStateOf<InspectionDrawnRoute?>(null) }
    val tabs = buildList {
        add(InspectionTab.Info)
        if (!cell?.samples.isNullOrEmpty()) add(InspectionTab.Samples)
        if (drawnRoutes.isNotEmpty()) add(InspectionTab.Routes)
    }
    val activeTab = selectedTab.takeIf { it in tabs } ?: InspectionTab.Info

    pendingRemoval?.let { route ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove drawn route?") },
            text = { Text("Places only this route cleared will be fogged again.") },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("confirm_remove_drawn_route"),
                    onClick = {
                        pendingRemoval = null
                        onRemoveDrawnRoute(route.id)
                    },
                ) { Text("Remove") }
            },
        )
    }

    Card(modifier = modifier.testTag("inspection_cell_details")) {
        Column {
            PrimaryTabRow(selectedTabIndex = tabs.indexOf(activeTab)) {
                tabs.forEach { tab ->
                    Tab(
                        selected = tab == activeTab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                when (tab) {
                                    InspectionTab.Info -> "Info"
                                    InspectionTab.Samples -> "Samples (${cell?.samples?.size ?: 0})"
                                    InspectionTab.Routes -> "Routes (${drawnRoutes.size})"
                                },
                            )
                        },
                    )
                }
            }
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (activeTab) {
                    InspectionTab.Info -> InspectionCellInfo(cell)
                    InspectionTab.Samples -> InspectionSamples(requireNotNull(cell), sort, onSortChange)
                    InspectionTab.Routes -> InspectionRoutes(drawnRoutes) { pendingRemoval = it }
                }
            }
        }
    }
}

@Composable
private fun InspectionCellInfo(cell: DiagnosticSampleCell?) {
    if (cell == null) {
        Text("Drawn Coverage Evidence")
    } else {
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
    }
}

@Composable
private fun InspectionSamples(
    cell: DiagnosticSampleCell,
    sort: SampleSort,
    onSortChange: (SampleSort) -> Unit,
) {
    TextButton(
        modifier = Modifier.testTag("inspection_sort"),
        onClick = {
            onSortChange(
                if (sort == SampleSort.NewestFirst) SampleSort.OldestFirst else SampleSort.NewestFirst,
            )
        },
    ) {
        Text(if (sort == SampleSort.NewestFirst) "Newest first" else "Oldest first")
    }
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

@Composable
private fun InspectionRoutes(
    drawnRoutes: List<InspectionDrawnRoute>,
    onRemove: (InspectionDrawnRoute) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .testTag("drawn_route_list"),
    ) {
        itemsIndexed(drawnRoutes, key = { _, route -> route.id }) { index, route ->
            if (index > 0) HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .testTag("drawn_route_${route.id}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Drawn route", maxLines = 1)
                    Text(
                        route.createdAtMillis.compactTime(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(
                    modifier = Modifier.testTag("remove_drawn_route_${route.id}"),
                    onClick = { onRemove(route) },
                ) {
                    Text("Remove", maxLines = 1)
                }
            }
        }
    }
}

internal val DiagnosticCellState.color: Color
    @Composable
    get() = when (this) {
        DiagnosticCellState.Observed -> MaterialTheme.colorScheme.primary
        DiagnosticCellState.Inferred -> MaterialTheme.colorScheme.tertiary
        DiagnosticCellState.RejectedOnly -> MaterialTheme.colorScheme.error
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

private val InspectionSource.label: String
    get() = when (this) {
        InspectionSource.Automatic -> "Automatic"
        InspectionSource.Recordings -> "Recordings"
        InspectionSource.DrawnRoutes -> "Drawn routes"
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
        val automatic = any { it.name.startsWith("Automatic") }
        val recording = any { it.name.startsWith("Recording") }
        return when {
            automatic && recording -> "Automatic + Recording"
            automatic -> "Automatic"
            recording -> "Recording"
            else -> "No source provenance"
        }
    }

private val DiagnosticSampleCell.coverageEvidenceLabel: String
    get() {
        val observed = provenance.any {
            it == DiagnosticProvenance.AutomaticObserved ||
                it == DiagnosticProvenance.RecordingObserved
        }
        val inferred = provenance.any {
            it == DiagnosticProvenance.AutomaticInferred ||
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
        recordingId?.let { "Recording" } ?: "Automatic",
        recordingId?.let { "Recording $it" } ?: trigger?.replace('_', ' '),
        activityMode.replace('_', ' ').replaceFirstChar(Char::uppercase),
    ).joinToString(" · ")
