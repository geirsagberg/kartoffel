package net.sagberg.kartoffel.prototype

// PROTOTYPE (throwaway, issue #34): Google Maps JSON styles per visual direction.

private const val QUIET =
    """{"featureType":"poi","stylers":[{"visibility":"off"}]},
       {"featureType":"transit","stylers":[{"visibility":"off"}]},
       {"featureType":"road","elementType":"labels.icon","stylers":[{"visibility":"off"}]}"""

private fun palette(
    land: String,
    road: String,
    arterial: String,
    water: String,
    park: String,
    label: String,
    halo: String,
    saturation: Int = 0,
) = """[
    {"stylers":[{"saturation":$saturation}]},
    {"elementType":"geometry","stylers":[{"color":"$land"}]},
    {"elementType":"labels.text.fill","stylers":[{"color":"$label"}]},
    {"elementType":"labels.text.stroke","stylers":[{"color":"$halo"}]},
    {"featureType":"road","elementType":"geometry","stylers":[{"color":"$road"}]},
    {"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"$arterial"}]},
    {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"$arterial"}]},
    {"featureType":"water","elementType":"geometry","stylers":[{"color":"$water"}]},
    {"featureType":"landscape.natural","elementType":"geometry","stylers":[{"color":"$park"}]},
    {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"$park"},{"visibility":"on"}]},
    {"featureType":"administrative","elementType":"geometry","stylers":[{"visibility":"off"}]},
    $QUIET
]"""

internal object PrototypeMapStyles {
    val harbourLight = "[$QUIET]"
    val harbourDark = palette(
        land = "#1d2329", road = "#2c353d", arterial = "#3a454f", water = "#0f1a24",
        park = "#1f2a24", label = "#8a949c", halo = "#1d2329",
    )
    val surveyLight = palette(
        land = "#f3f4f5", road = "#ffffff", arterial = "#ffffff", water = "#d5dade",
        park = "#e6e8e6", label = "#6b7177", halo = "#f3f4f5", saturation = -100,
    )
    val surveyDark = palette(
        land = "#202326", road = "#2f3337", arterial = "#3a3f44", water = "#121417",
        park = "#23272a", label = "#7d848b", halo = "#202326", saturation = -100,
    )
    val lanternLight = palette(
        land = "#eceaf0", road = "#ffffff", arterial = "#ffffff", water = "#c9cfe6",
        park = "#dfe6da", label = "#5e5870", halo = "#eceaf0",
    )
    val lanternDark = palette(
        land = "#221d2d", road = "#342c45", arterial = "#3f3653", water = "#0d0b14",
        park = "#1a2019", label = "#8a829c", halo = "#17141f",
    )
}
