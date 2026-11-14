# Color Data Attribution

The MARD 291-color data used by Pixel Bead is sourced from:

- Project: **makebead/craft-color-codes**
- File: `data/csv/mard-291.csv`
- Repository: https://github.com/makebead/craft-color-codes
- Retrieved: 2026-11-15 (branch `main`)
- License: **MIT** — Copyright (c) 2026 MakeBead (https://makebead.com). See `LICENSE`.

The CSV is vendored verbatim as `mard-291.csv`. At build time the `generateBeadResources`
Gradle task derives the canonical palette JSON and all per-color block/item/loot/lang
resources from this single file, and hard-fails if the color count (291) or per-series
counts (A26 B32 C29 D26 E24 F25 G21 H23 M15 P23 Q5 R28 T1 Y5 ZG8) do not match.

## Accuracy notice

MARD HEX/RGB values are **screen reference values** compiled from public color charts.
They are not manufacturer-measured official values. Different charts, batches, materials
and displays may vary. The data version is pinned by the vendored CSV in this directory.