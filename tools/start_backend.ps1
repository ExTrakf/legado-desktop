# Legado Desktop backend - start script (Windows PowerShell)
# Linux/macOS: use tools/start_backend.sh instead.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools/start_backend.ps1 [-build] [backend args...]
#     -build            force re-run installDist (default: only if not installed)
#     backend args...   passed through, e.g. -port 2323 -host 127.0.0.1
#                       -set-js-source-token <token>, -api-smoke-test, ...
#
# Env:
#   LEGADO_DESKTOP_HOME         data dir (default %USERPROFILE%\.legado-desktop)
#   LEGADO_DESKTOP_ENABLE_JCEF  set to 1 to enable the JCEF webview engine
#
# NOTE: ASCII-only output (lesson 43: PS 5.1 reads UTF-8-no-BOM as GBK).
$ErrorActionPreference = "Stop"

if ($args -contains "-help" -or $args -contains "-h") {
    Get-Content -LiteralPath $PSCommandPath | Select-Object -First 14
    exit 0
}

$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "backend"
$binDir = Join-Path $backend "build\install\legado-desktop-backend\bin"
$bat = Join-Path $binDir "legado-desktop-backend.bat"

$build = $false
$pass = @()
foreach ($a in $args) {
    if ($a -eq "-build") { $build = $true } else { $pass += $a }
}

if (-not $env:LEGADO_DESKTOP_HOME) {
    $env:LEGADO_DESKTOP_HOME = Join-Path $HOME ".legado-desktop"
    Write-Host "[start-backend] LEGADO_DESKTOP_HOME=$env:LEGADO_DESKTOP_HOME"
}

if ($build -or -not (Test-Path $bat)) {
    Write-Host "[start-backend] running installDist ..."
    Push-Location $backend
    & .\gradlew.bat installDist --console=plain
    if ($LASTEXITCODE -ne 0) { Pop-Location; exit $LASTEXITCODE }
    Pop-Location
}

Write-Host "[start-backend] starting backend: $bat $($pass -join ' ')"
& $bat @pass
exit $LASTEXITCODE
