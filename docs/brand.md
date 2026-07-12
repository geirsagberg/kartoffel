# Kartoffel brand

## Color direction

The app uses **Trailhead**, a map-friendly palette that avoids the generic purple Material baseline:

- evergreen `#174F45` anchors the identity and launcher background;
- clear-water teal `#31C6A0` represents revealed coverage and active states;
- trail orange `#F39A3F` highlights routes and selected actions;
- pale mint `#D6F5E8` keeps the mark legible at launcher size.

The Compose theme derives accessible light and dark Material roles from the same direction. Colors must be consumed through `MaterialTheme.colorScheme`; pair each container role with its corresponding `on…` role.

Other viable future directions are **Cartographic** (deep blue, turquoise, coral) and **Expedition** (burnt orange, pine, sky blue). Trailhead is preferred because it remains distinctive over Google Maps without competing with map labels and markers.

## Icon

The icon combines a folded map with a winding orange trail. It is original project artwork, provided as an Android adaptive foreground, evergreen background, and monochrome themed-icon layer. Keep important geometry inside the adaptive-icon safe zone and avoid adding text, gradients, or location-pin imagery.
