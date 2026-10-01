#Requires -Version 5.1
# Stop the local ProdTestServer (RhythMC-Charter-V2 plugin test server).
#
# start.bat is a ":E ... goto :E" endless loop (auto-restarts on crash AND on
# normal stop), so both the cmd loop window and the java server process must die:
#   0. .prod-test.pid (window launched via script, most precise);
#   1. cmd.exe windows whose command line contains ProdTestServer (start.bat path);
#   2. java.exe holding the server port from server.properties (the java command
#      line only has a relative jar path, so port matching is the reliable key).
# Kills that fail fall back to taskkill; up to 3 rounds. Exit 0 when nothing runs.

$ErrorActionPreference = 'Continue'

$serverDir = Join-Path (Split-Path $PSScriptRoot -Parent) 'ProdTestServer'

$port = 25565
$props = Join-Path $serverDir 'server.properties'
if (Test-Path $props) {
    $m = Select-String -Path $props -Pattern '^\s*server-port\s*=\s*(\d+)' | Select-Object -First 1
    if ($null -ne $m) { $port = [int]$m.Matches[0].Groups[1].Value }
}

$killed = New-Object System.Collections.Generic.List[string]

function Find-ServerPids {
    $cmdIds = @(Get-CimInstance Win32_Process -Filter "Name='cmd.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -like '*ProdTestServer*' } |
        ForEach-Object { [int]$_.ProcessId })
    $javaIds = New-Object System.Collections.Generic.List[int]
    try {
        Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
            ForEach-Object {
                $hp = Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue
                if ($null -ne $hp -and $hp.ProcessName -eq 'java') { $javaIds.Add([int]$_.OwningProcess) }
            }
    } catch {}
    return @{ Cmd = $cmdIds; Java = $javaIds }
}

function Kill-Pids {
    param([int[]]$Ids, [string]$Why)
    foreach ($id in ($Ids | Sort-Object -Unique)) {
        if (-not (Get-Process -Id $id -ErrorAction SilentlyContinue)) { continue }
        try {
            Stop-Process -Id $id -Force -ErrorAction Stop
            $killed.Add($Why + ':' + $id)
        } catch {
            & taskkill /F /PID $id 2>&1 | Out-Null
            if (-not (Get-Process -Id $id -ErrorAction SilentlyContinue)) { $killed.Add($Why + '(taskkill):' + $id) }
            else { Write-Warning ("[stop-prod-test] cannot kill " + $Why + " pid " + $id + " : " + $_.Exception.Message) }
        }
    }
}

# Step 0: pid file first (a live cmd loop window would restart java otherwise).
$pidFile = Join-Path $serverDir '.prod-test.pid'
if (Test-Path $pidFile) {
    $old = (Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1) -as [int]
    if ($old) { Kill-Pids -Ids @($old) -Why 'cmd(pidfile)' }
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

# Up to 3 rounds: kill cmd loop windows first, then port-holding java.
for ($round = 1; $round -le 3; $round++) {
    $found = Find-ServerPids
    if ($found.Cmd.Count -eq 0 -and $found.Java.Count -eq 0) { break }
    Kill-Pids -Ids $found.Cmd -Why 'cmd'
    Start-Sleep -Seconds 2
    $found = Find-ServerPids
    Kill-Pids -Ids $found.Java -Why 'java'
    Start-Sleep -Seconds 2
}

# Final check (JVM shutdown is slow, allow up to 20s).
$deadline = (Get-Date).AddSeconds(20)
do {
    Start-Sleep -Seconds 1
    $found = Find-ServerPids
} while (((Get-Date) -lt $deadline) -and ($found.Cmd.Count -gt 0 -or $found.Java.Count -gt 0))

if ($killed.Count -eq 0 -and $found.Cmd.Count -eq 0 -and $found.Java.Count -eq 0) {
    Write-Host '[stop-prod-test] server not running, nothing to do.'
} elseif ($found.Cmd.Count -eq 0 -and $found.Java.Count -eq 0) {
    Write-Host ('[stop-prod-test] stopped: ' + ($killed -join ', '))
} else {
    Write-Warning ('[stop-prod-test] still alive after kill (port ' + $port + ' / ProdTestServer cmd+java: ' + ($found.Cmd -join ',') + ' / ' + ($found.Java -join ',') + '), please check Task Manager.')
    exit 1
}
exit 0
