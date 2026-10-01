@echo off
REM RhythMC-Charter-V2 一键部署到本地测试服 ProdTestServer 并开服（双击即用）。
REM 参数透传给 deploy-prod-test.ps1，例如：deploy-prod-test.bat -SkipTests / -NoStart
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy-prod-test.ps1" %*
if errorlevel 1 (
  echo [deploy] FAILED, see output above.
  pause
  exit /b 1
)
endlocal
