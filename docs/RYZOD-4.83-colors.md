# RYZOD 4.83 color update

The user requested orange primary navigation bubbles, silver category/menu bubbles, light Carolina-blue channel bubbles, and lettering that looks more gold than yellow.

## Palette

| Use | Color |
| --- | --- |
| Primary navigation | `#E29145` |
| Selected primary navigation | `#F1AE70` |
| Categories, Favorites and Main menu return | `#C9CDD1` |
| Selected category | `#E5E7E9` |
| Guide channel bubbles | `#A6CBE6` |
| Gold lettering on light fills | `#4B2F05` |
| Program title lettering on dark surfaces | `#E3BA65` |
| Gold accent | `#D4A548` |

The deeper gold is an implementation choice to keep light silver/blue/orange surfaces readable. Calculated text contrast is 4.90:1 on orange, 6.46:1 on selected orange, 7.69:1 on silver, 9.92:1 on selected silver and 7.21:1 on light blue. Program lettering is 8.36:1 on the standard dark card surface.

Navigation callbacks, focus behavior, dimensions, search retention, guide persistence and buffering policy are unchanged. Selection still has a distinct background, border, dot and font weight; the existing remote focus outline remains.

The local execution environment was unavailable for this update. Source changes were prepared through the connected repository; the inherited release pipeline performs full Android/Fire TV tests, lint, minified signed builds, native alignment, exact-source and monotonic updater checks remotely. No local build or screenshot inspection is claimed for this color update.

No additional implementation-mirroring tests are added for the palette-only change. Existing navigation and guide UI regressions run with the complete release suites.
