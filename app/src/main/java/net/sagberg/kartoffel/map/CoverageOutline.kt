package net.sagberg.kartoffel.map

import kotlin.math.roundToLong
import net.sagberg.kartoffel.coverage.CoverageCellShape
import net.sagberg.kartoffel.coverage.GeoCoordinate

/**
 * Returns the closed rings that trace the edge of the given cells' union. Edges shared by two
 * cells are interior and dropped; the remaining edges are chained into rings. Each ring lists its
 * vertices once, without repeating the first vertex at the end. Input must be non-overlapping
 * cells from the same H3 resolution, as used by Street-Level Coverage.
 */
internal fun coverageOutline(cells: List<CoverageCellShape>): List<List<GeoCoordinate>> {
    val edges = cells.flatMap { cell ->
        cell.boundary.indices.map { index ->
            OutlineEdge(cell.boundary[index], cell.boundary[(index + 1) % cell.boundary.size])
        }
    }
    val edgeCounts = edges.groupingBy { it.undirectedKey }.eachCount()
    val outgoing = edges
        .filter { edgeCounts.getValue(it.undirectedKey) == 1 }
        .groupByTo(LinkedHashMap()) { it.from.key }
        .mapValuesTo(LinkedHashMap()) { (_, vertexEdges) -> ArrayDeque(vertexEdges) }

    val rings = mutableListOf<List<GeoCoordinate>>()
    while (outgoing.isNotEmpty()) {
        val startKey = outgoing.keys.first()
        val ring = mutableListOf<GeoCoordinate>()
        var key = startKey
        while (true) {
            val vertexEdges = outgoing[key] ?: break
            val edge = vertexEdges.removeFirst()
            if (vertexEdges.isEmpty()) outgoing.remove(key)
            ring += edge.from
            key = edge.to.key
            if (key == startKey) break
        }
        if (ring.size >= 3) rings += ring
    }
    return rings
}

private data class OutlineEdge(
    val from: GeoCoordinate,
    val to: GeoCoordinate,
) {
    val undirectedKey: Set<VertexKey> get() = setOf(from.key, to.key)
}

private data class VertexKey(
    val latitude: Long,
    val longitude: Long,
)

// Neighbouring H3 cells compute their shared vertices separately, so match them at a precision
// far below a pixel at any map zoom instead of relying on exact floating-point equality.
private const val VERTEX_PRECISION = 1e9

private val GeoCoordinate.key: VertexKey
    get() = VertexKey(
        latitude = (latitude * VERTEX_PRECISION).roundToLong(),
        longitude = (longitude * VERTEX_PRECISION).roundToLong(),
    )
