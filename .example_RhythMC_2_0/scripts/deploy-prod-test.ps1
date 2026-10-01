#Requires -Version 5.1
# RhythMC-Charter-V2: one-click deploy to local test server ProdTestServer + start.
#
#   1. mvn clean package -Pprod-test (unit tests + copy jar to
#      ProdTestServer/plugins/RhythMC-Charter-V2.jar, stale jars cleaned);
#   2. stop a running test server (scripts/stop-prod-test.ps1);
#   3. start ProdTestServer (start.bat, hidden window) and wait for the
#      Done line + our plugin Enabling line in logs/latest.log.
#
# Params: -SkipTests (skip unit tests), -NoStart (build+deploy only).

param(
    [switch]$SkipTests,
    [switch]$NoStart
)
$ErrorActionPreference = 'Stop'

$root = Split-Path $PSScriptRoot -Parent
$serverDir = Join-Path $root 'ProdTestServer'
$pluginJar = Join-Path $serverDir 'plugins\RhythMC-Charter-V2.jar'
$startBat = Join-Path $serverDir 'start.bat'

if (-not (Test-Path $startBat)) { throw "not found: $startBat" }

$buildArgs = @('clean', 'package', '-P', 'prod-test')
if ($SkipTests) { $buildArgs += '-DskipTests' }
Write-Host ('[deploy] ==> mvn.cmd ' + ($buildArgs -join ' '))
& mvn.cmd @buildArgs
if ($LASTEXITCODE -ne 0) { throw "maven build failed (exit $LASTEXITCODE)" }

$dst = Get-Item $pluginJar -ErrorAction SilentlyContinue
if ($null -eq $dst) { throw "deploy copy missing: $pluginJar (check [prod-test] antrun output above)" }
$ageMin = ((Get-Date) - $dst.LastWriteTime).TotalMinutes
if ($ageMin -gt 10) { throw ("deployed jar looks stale (" + $dst.LastWriteTime + "), aborting start") }
Write-Host ('[deploy] deployed OK: plugins\RhythMC-Charter-V2.jar (' + [math]::Round($dst.Length / 1MB, 1) + ' MB)')

if ($NoStart) { Write-Host '[deploy] -NoStart, done (server not started).'; exit 0 }

& "$PSScriptRoot\stop-prod-test.ps1"
if ($LASTEXITCODE -ne 0) { throw 'stop-prod-test failed, aborting start' }

Write-Host '[deploy] starting ProdTestServer (hidden window)...'
& "$PSScriptRoot\start-prod-test.ps1"
if ($LASTEXITCODE -ne 0) { throw 'start-prod-test failed, aborting' }

# Wait for boot: Done + our plugin Enabling in logs/latest.log (up to ~150s).
$logPath = Join-Path $serverDir 'logs\latest.log'
$deadline = (Get-Date).AddSeconds(150)
$done = $false
$pluginOk = $false
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 3
    if (Test-Path $logPath) {
        $logAge = ((Get-Date) - (Get-Item $logPath).LastWriteTime).TotalMinutes
        if ($logAge -lt 5) {
            if (-not $done -and (Select-String -Path $logPath -Pattern 'Done \(' -Quiet)) { $done = $true }
            if (-not $pluginOk -and (Select-String -Path $logPath -Pattern 'Enabling RhythMC-Charter-V2' -Quiet)) { $pluginOk = $true }
            if ($done) { break }
        }
    }
}

if ($done -and $pluginOk) {
    Write-Host '[deploy] server UP, RhythMC-Charter-V2 enabled. Connect: localhost:25565 (offline-mode).'
    exit 0
} elseif ($done) {
    Write-Warning '[deploy] server is UP but RhythMC-Charter-V2 Enabling line not found in log - check console for plugin errors.'
    exit 1
} else {
    Write-Warning '[deploy] timed out waiting for server boot - check ProdTestServer/logs/latest.log.'
    exit 1
}
