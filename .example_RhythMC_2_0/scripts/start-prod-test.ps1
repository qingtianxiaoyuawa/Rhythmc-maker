#Requires -Version 5.1
# Start the local ProdTestServer (RhythMC-Charter-V2 plugin test server) in a
# VISIBLE console window, detached.
#
# JVM flags stay owned by ProdTestServer/start.bat (not duplicated here).
# Notes:
#   - visible window first (new console running start.bat, so you can watch
#     logs / type commands); falls back to hidden only when no desktop
#     session is available (e.g. headless CI);
#   - the window cmd pid is stored in ProdTestServer/.prod-test.pid for stop.

$ErrorActionPreference = 'Stop'

$serverDir = Join-Path (Split-Path $PSScriptRoot -Parent) 'ProdTestServer'
$startBat = Join-Path $serverDir 'start.bat'
$pidFile = Join-Path $serverDir '.prod-test.pid'

if (-not (Test-Path $startBat)) { throw "not found: $startBat" }

$port = 25565
$props = Join-Path $serverDir 'server.properties'
if (Test-Path $props) {
    $m = Select-String -Path $props -Pattern '^\s*server-port\s*=\s*(\d+)' | Select-Object -First 1
    if ($null -ne $m) { $port = [int]$m.Matches[0].Groups[1].Value }
}
$busy = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
if ($null -ne $busy) { throw ("port " + $port + " already in use (PID " + $busy[0].OwningProcess + "), run stop-prod-test.ps1 first") }

$proc = $null
try {
    # Visible console window running start.bat (preferred: you can watch it).
    $proc = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', 'start', '"ProdTestServer"', '/d', ('"' + $serverDir + '"'), ('"' + $startBat + '"') `
        -WorkingDirectory $serverDir -WindowStyle Normal -PassThru
    Start-Sleep -Seconds 2
    $proc.Refresh()
    if ($proc.HasExited -and $proc.ExitCode -ne 0) { throw 'visible start failed' }
    Write-Host '[start-prod-test] ProdTestServer console window opened (start.bat).'
    # NOTE: `start` detaches, so this launcher pid exits at once and is NOT
    # the server window; stop-prod-test finds the window by its
    # "ProdTestServer" title / command line + the listening port instead.
    if (Test-Path $pidFile) { Remove-Item $pidFile -Force }
} catch {
    # No desktop session (headless): fall back to hidden window.
    Write-Warning ('[start-prod-test] visible window unavailable (' + $_.Exception.Message + '), falling back to hidden window.')
    $proc = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', ('"' + $startBat + '"') `
        -WorkingDirectory $serverDir -WindowStyle Hidden -PassThru
    Start-Sleep -Seconds 5
    $proc.Refresh()
    if ($proc.HasExited) { throw 'server process exited immediately - check java path in ProdTestServer/start.bat' }
    Write-Host ('[start-prod-test] launched hidden (cmd pid ' + $proc.Id + ').')
    $proc.Id | Set-Content $pidFile -NoNewline
}

Write-Host '[start-prod-test] Boot ~15s; watch the console or: Get-Content ProdTestServer\logs\latest.log -Tail 5'
