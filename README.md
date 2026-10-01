# Rhythmc maker

## Launch the test client

Run the following command after cloning the project:

```powershell
.\gradlew.bat buildAndRunClient
```

This compiles the project first, then starts Fabric Loom's development client with RhythMC Maker and its bundled Arcade runtime dependencies. The client task is intentionally separate from the normal `build` task so CI and packaging builds do not open a Minecraft window.

Fabric mod for Minecraft 1.21.11.

A useful tool to make Rhythmc3.0 chart
