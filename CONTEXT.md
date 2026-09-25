# RhythMC Maker Context

## Purpose

RhythMC Maker is a Fabric client-side chart editor for RhythMC 3.0. It provides chart editing, local audio playback, timeline seeking, speed/BPM visualization, and scene/arena preview.

## Project paths

- Main Fabric project: `F:\b晴天小雨awa\RhythMC\rhythmc maker`
- Related Paper/Java project: `C:\Users\28585\Documents\Rhythmc maker\_charter_v2_readonly\RhythMC-Charter-V2-master`

## Domain model

- `beat` is the authoritative chart time unit.
- `BPM` maps beat positions to milliseconds.
- `speedEvents` control chart note travel distance along a track.
- Player/preview speed is a separate multiplier and must not silently alter chart data.
- Audio position is the transport clock at the playback boundary.
- Scene/arena structures are static or previewable world geometry and are separate from notes.

## Format boundaries

- `.rmcc`: RhythMC runtime chart format.
- `.rmcd`: editor draft/work format.
- `.schem`: WorldEdit scene/arena structure format.
- `manifest.yml`: chart package metadata.

Do not merge scene structure data into note data unless the RhythMC 3.0 contract explicitly requires it.

## Playback requirements

The playback layer should expose play, pause, stop, seek, loop, speed, and current-position operations. Seeking must directly evaluate chart and scene state at the target time instead of replaying every prior event.

## Rendering requirements

Scene rendering and note rendering should be driven from a shared timeline state, while keeping visual rendering separate from actual world collision. The editor should warn about scene geometry that occludes notes or blocks required player movement.

## Related implementation notes

The related Paper project contains design references for `Compiler`, `PreviewRenderer`, `ChartMath`, `BeatClock`, `Transport`, `.rmcd`, `.rmcc`, and `.schem`. Treat that project as a reference, not as a reason to couple Fabric code to Bukkit/Paper APIs.
