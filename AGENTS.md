# RhythMC Maker Project Instructions

## Scope

These instructions apply to the RhythMC Maker Fabric client mod project.

## Project context

- Primary project: `F:\b晴天小雨awa\RhythMC\rhythmc maker`
- Related Paper/Java project: `C:\Users\28585\Documents\Rhythmc maker\_charter_v2_readonly\RhythMC-Charter-V2-master`
- RhythMC 3.0 reference/runtime project: consult the related project and local documentation before changing format or gameplay semantics.

## Technology

- Minecraft Fabric client mod
- Java/Gradle project
- Client-side rendering, audio playback, chart editing, and scene preview

## Engineering rules

- At the start of each work conversation, follow the RhythMC Maker collaboration protocol: read the unprocessed joint_maker\planner+*.md files only when the current request asks to apply planner guidance, then prioritize the user's current request.
- After any actual implementation, build, or repair work, write a joint_maker\maker+yyyyMMdd-HHmmss.md record containing the core work summary, workflow, changed files, validation results, unfinished items, blockers, and next-step suggestions. If no work is performed, do not create a maker record.
- After a successful build, automatically deploy `build\libs\rhythmc-maker-0.1.0.jar` to `F:\b晴天小雨awa\程序\我的世界PCL\.minecraft\versions\Rhythmc 3.0\mods`, replacing the existing Rhythmc Maker mod. Before replacement, move the existing matching Rhythmc Maker JAR to `F:\b晴天小雨awa\RhythMC\rhythmc maker\bak` with a `bak-yyyyMMdd-HHmmss` timestamp suffix. Do not delete unrelated mods or the five separately installed Arcade JARs unless the user explicitly requests it.
- Keep Fabric client responsibilities separate from Paper server responsibilities.
- Preserve RhythMC 3.0 compatibility for `.rmcc`, scene/arena data, timing, BPM, and speed events.
- Treat `.rmcc` as runtime chart data, `.rmcd` as editor draft data, and `.schem` as scene/arena structure data.
- Use beat as the chart timing domain; convert to milliseconds only at audio and transport boundaries.
- Audio playback must support seeking from arbitrary positions without making rendering depend on frame count.
- Scene preview must distinguish visual occlusion from gameplay collision.
- Keep changes focused and avoid unrelated refactors.
- Do not commit changes or create branches unless explicitly requested.

## Validation

- Inspect the changed code and configuration before running commands.
- Prefer targeted Gradle tests or checks for changed modules.
- Do not fix unrelated failures.
- Report when validation is not run.
