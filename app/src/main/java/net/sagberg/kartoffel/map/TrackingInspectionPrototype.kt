package net.sagberg.kartoffel.map

// PROTOTYPE — throw this code away after issue #28 is resolved.
// One Tracking Inspection interaction on the existing Coverage Map.
// Run with one command: scripts/deploy-phone.sh

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import net.sagberg.kartoffel.coverage.GeoCoordinate
import net.sagberg.kartoffel.coverage.H3CoverageCells

@Composable
internal fun TrackingInspectionPrototype(onExit: () -> Unit) {
    val cells = remember { createPrototypeCells() }
    var scope by rememberSaveable { mutableStateOf("All tracking") }
    var time by rememberSaveable { mutableStateOf("All time") }
    var selectedCellId by rememberSaveable { mutableStateOf<String?>(null) }
    var filtersVisible by rememberSaveable { mutableStateOf(false) }
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    val selectedCell = cells.firstOrNull { it.id == selectedCellId }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("tracking_inspection_prototype"),
    ) {
        PrototypeMapLayer(
            cells = cells,
            selectedCellId = selectedCellId,
            onSelect = { selectedCellId = it.id },
        )

        InspectionControls(
            scope = scope,
            time = time,
            filtersVisible = filtersVisible,
            onScopeChange = { scope = it },
            onTimeChange = { time = it },
            onFiltersVisibleChange = { filtersVisible = it },
            onExit = onExit,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp)
                .fillMaxWidth(),
        )

        if (!filtersVisible) {
            selectedCell?.let { cell ->
                CellDetailsCard(
                    cell = cell,
                    newestFirst = newestFirst,
                    onSortChange = { newestFirst = !newestFirst },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(12.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PrototypeMapLayer(
    cells: List<PrototypeCell>,
    selectedCellId: String?,
    onSelect: (PrototypeCell) -> Unit,
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(prototypeMapCenter, 17.4f)
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
        ),
    ) {
        cells.forEach { cell ->
            val selected = cell.id == selectedCellId
            Polygon(
                points = cell.boundary,
                clickable = true,
                fillColor = cell.category.color.copy(alpha = 0.72f),
                strokeColor = if (selected) Color.White else Color.Black.copy(alpha = 0.6f),
                strokeWidth = if (selected) 9f else 3f,
                zIndex = if (selected) 2f else 1f,
                onClick = { onSelect(cell) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InspectionControls(
    scope: String,
    time: String,
    filtersVisible: Boolean,
    onScopeChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onFiltersVisibleChange: (Boolean) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
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
                    CyclingFilterChip(
                        label = scope,
                        choices = prototypeScopes,
                        onChange = onScopeChange,
                    )
                    CyclingFilterChip(
                        label = time,
                        choices = prototypeTimes,
                        onChange = onTimeChange,
                    )
                }
                Row {
                    TextButton(onClick = { onFiltersVisibleChange(!filtersVisible) }) {
                        Text(if (filtersVisible) "Done" else "Filters")
                    }
                    TextButton(onClick = onExit) { Text("Exit") }
                }
            }
            if (filtersVisible) {
                HorizontalDivider()
                FilterGroup("Scope", prototypeScopes, scope, onScopeChange)
                FilterGroup("Time", prototypeTimes, time, onTimeChange)
                if (time == "Custom") CustomTimeWindow()
            }
            PrototypeLegend()
        }
    }
}

@Composable
private fun CyclingFilterChip(
    label: String,
    choices: List<String>,
    onChange: (String) -> Unit,
) {
    FilterChip(
        selected = label != choices.first(),
        onClick = {
            val nextIndex = (choices.indexOf(label) + 1).mod(choices.size)
            onChange(choices[nextIndex])
        },
        label = { Text(label) },
    )
}

@Composable
private fun FilterGroup(
    label: String,
    choices: List<String>,
    selected: String,
    onChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(choices) { choice ->
                FilterChip(
                    selected = choice == selected,
                    onClick = { onChange(choice) },
                    label = { Text(choice) },
                )
            }
        }
    }
}

@Composable
private fun CustomTimeWindow() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = true,
            onClick = {},
            label = { Text("Jul 25 · 00:00") },
        )
        FilterChip(
            selected = true,
            onClick = {},
            label = { Text("Aug 1 · 19:00") },
        )
    }
}

@Composable
private fun CellDetailsCard(
    cell: PrototypeCell,
    newestFirst: Boolean,
    onSortChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var samplesVisible by rememberSaveable(cell.id) { mutableStateOf(false) }

    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CellSummary(cell)
            HorizontalDivider()

            if (cell.samples.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { samplesVisible = !samplesVisible }) {
                        Text(if (samplesVisible) "Hide samples" else "Samples (${cell.samples.size})")
                    }
                    if (samplesVisible) {
                        TextButton(onClick = onSortChange) {
                            Text(if (newestFirst) "Newest first" else "Oldest first")
                        }
                    }
                }
                if (samplesVisible) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .testTag("sample_history"),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val samples = if (newestFirst) cell.samples else cell.samples.reversed()
                        items(samples) { sample ->
                            Text(
                                sample,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            } else {
                cell.evidence.forEach { evidence ->
                    Text(
                        evidence,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun CellSummary(cell: PrototypeCell) {
    Text(cell.category.displayName, style = MaterialTheme.typography.titleMedium)
    Text("Diagnostic Sample Cell ${cell.id}", style = MaterialTheme.typography.labelSmall)
    Text("${cell.acceptedCount} accepted · ${cell.rejectedCount} rejected · ${cell.provenance}")
    Text("${cell.coverageEvidence} · ${cell.timeSpan}")
}

@Composable
private fun PrototypeLegend() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(PrototypeCellCategory.entries) { category ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(category.color, RoundedCornerShape(50)),
                )
                Text(category.shortName, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private enum class PrototypeCellCategory(
    val shortName: String,
    val displayName: String,
    val color: Color,
) {
    OBSERVED("Observed", "Observed", Color(0xFF147D3F)),
    INFERRED("Inferred", "Inferred", Color(0xFF5B4ACB)),
    REJECTED_ONLY("Rejected-only", "Rejected-only", Color(0xFFB3261E)),
}

private data class PrototypeCell(
    val id: String,
    val boundary: List<LatLng>,
    val category: PrototypeCellCategory,
    val acceptedCount: Int,
    val rejectedCount: Int,
    val provenance: String,
    val coverageEvidence: String,
    val timeSpan: String,
    val samples: List<String>,
    val evidence: List<String>,
)

private data class PrototypeCellSeed(
    val coordinate: GeoCoordinate,
    val category: PrototypeCellCategory,
    val acceptedCount: Int,
    val rejectedCount: Int,
    val provenance: String,
    val coverageEvidence: String,
    val timeSpan: String,
    val samples: List<String> = emptyList(),
    val evidence: List<String> = emptyList(),
)

private val prototypeScopes = listOf(
    "All tracking",
    "Passive",
    "All Recording Sessions",
    "Today · 09:12 · 24 min",
    "Jul 31 · 18:06 · 31 min",
)
private val prototypeTimes = listOf("All time", "24 hours", "7 days", "Custom")
private val prototypeMapCenter = LatLng(59.9107, 10.7521)

private fun createPrototypeCells(): List<PrototypeCell> {
    val h3 = H3CoverageCells()
    return prototypeCellSeeds.map { seed ->
        val cellId = h3.cellAt(seed.coordinate)
        PrototypeCell(
            id = "…${cellId.value.toString(16).takeLast(6)}",
            boundary = h3.boundaryOf(cellId).map { LatLng(it.latitude, it.longitude) },
            category = seed.category,
            acceptedCount = seed.acceptedCount,
            rejectedCount = seed.rejectedCount,
            provenance = seed.provenance,
            coverageEvidence = seed.coverageEvidence,
            timeSpan = seed.timeSpan,
            samples = seed.samples,
            evidence = seed.evidence,
        )
    }.distinctBy { it.id }
}

private val prototypeCellSeeds = listOf(
    PrototypeCellSeed(
        coordinate = GeoCoordinate(59.9113, 10.7510),
        category = PrototypeCellCategory.OBSERVED,
        acceptedCount = 3,
        rejectedCount = 0,
        provenance = "Passive",
        coverageEvidence = "Observed Coverage Evidence",
        timeSpan = "Yesterday 08:41–Today 08:42",
        samples = listOf(
            "Today 08:42 · Accepted · 9 m · Passive · Movement · Walking",
            "Today 08:41 · Accepted · 12 m · Passive · Movement · Walking",
            "Yesterday 08:41 · Accepted · 18 m · Passive · Fallback · Unknown",
        ),
    ),
    PrototypeCellSeed(
        coordinate = GeoCoordinate(59.9114, 10.7522),
        category = PrototypeCellCategory.OBSERVED,
        acceptedCount = 16,
        rejectedCount = 6,
        provenance = "Recording Session · Today 09:12",
        coverageEvidence = "Observed Coverage Evidence",
        timeSpan = "Today 09:15–09:36",
        samples = List(22) { index ->
            val minute = 36 - index
            val accepted = index % 4 != 1
            val accuracy = if (accepted) 7 + (index % 9) else 51 + index
            "Today 09:${minute.toString().padStart(2, '0')} · " +
                "${if (accepted) "Accepted" else "Rejected"} · $accuracy m · " +
                "Recording 09:12 · Walking"
        },
    ),
    PrototypeCellSeed(
        coordinate = GeoCoordinate(59.9112, 10.7534),
        category = PrototypeCellCategory.INFERRED,
        acceptedCount = 0,
        rejectedCount = 0,
        provenance = "Recording Session · Today 09:12",
        coverageEvidence = "Inferred Coverage Evidence",
        timeSpan = "Today 09:13",
        evidence = listOf("Today 09:13 · Inferred · Recording 09:12"),
    ),
    PrototypeCellSeed(
        coordinate = GeoCoordinate(59.9106, 10.7509),
        category = PrototypeCellCategory.REJECTED_ONLY,
        acceptedCount = 0,
        rejectedCount = 2,
        provenance = "Recording Session · Jul 31 18:06",
        coverageEvidence = "No Coverage Evidence",
        timeSpan = "Jul 31 18:06–18:07",
        samples = listOf(
            "Jul 31 18:07 · Rejected · 74 m · Recording 18:06 · Cycling",
            "Jul 31 18:06 · Rejected · 93 m · Recording 18:06 · Cycling",
        ),
    ),
    PrototypeCellSeed(
        coordinate = GeoCoordinate(59.9105, 10.7521),
        category = PrototypeCellCategory.OBSERVED,
        acceptedCount = 7,
        rejectedCount = 0,
        provenance = "Passive + Recording Session",
        coverageEvidence = "Observed Coverage Evidence",
        timeSpan = "Jul 30 17:52–Today 09:20",
        samples = List(7) { index ->
            val recording = index < 4
            "${if (recording) "Today 09:${20 - index}" else "Jul 30 17:${58 - index}"} · " +
                "Accepted · ${8 + index} m · " +
                "${if (recording) "Recording 09:12 · Walking" else "Passive · Movement · Walking"}"
        },
    ),
    PrototypeCellSeed(
        coordinate = GeoCoordinate(59.9104, 10.7533),
        category = PrototypeCellCategory.INFERRED,
        acceptedCount = 0,
        rejectedCount = 1,
        provenance = "Passive",
        coverageEvidence = "Inferred Coverage Evidence",
        timeSpan = "Yesterday 14:03–14:04",
        samples = listOf(
            "Yesterday 14:04 · Rejected · 58 m · Passive · Movement · Walking",
        ),
    ),
    PrototypeCellSeed(
        coordinate = GeoCoordinate(59.9098, 10.7521),
        category = PrototypeCellCategory.OBSERVED,
        acceptedCount = 2,
        rejectedCount = 3,
        provenance = "Recording Session · Jul 31 18:06",
        coverageEvidence = "Observed + Inferred Coverage Evidence",
        timeSpan = "Jul 31 18:09–18:13",
        samples = List(5) { index ->
            val accepted = index < 2
            "Jul 31 18:${13 - index} · ${if (accepted) "Accepted" else "Rejected"} · " +
                "${if (accepted) 6 + index else 61 + index} m · Recording 18:06 · Cycling"
        },
    ),
)
