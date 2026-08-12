# Compose frontend self-check (Windows PowerShell): compile + package + launch smoke
# Usage: powershell -ExecutionPolicy Bypass -File tools/check_frontend.ps1
# NOTE: ASCII-only output (lesson 28); requires a display for the GUI launch smoke.
$ErrorActionPreference = "Continue"
$PASS = 0; $FAIL = 0
function ok($m)  { $script:PASS++; Write-Host "  [PASS] $m" }
function bad($m) { $script:FAIL++; Write-Host "  [FAIL] $m" }

$root = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $root "composeApp"
$backend = Join-Path $root "backend"

Write-Host "== 1. compile + desktopJar =="
Push-Location $compose
& .\gradlew.bat compileKotlinDesktop desktopJar --console=plain -q 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) { ok "compileKotlinDesktop + desktopJar" } else { bad "frontend compile failed (exit=$LASTEXITCODE)" }
Pop-Location

Write-Host "== 2. createDistributable =="
Push-Location $compose
& .\gradlew.bat createDistributable --console=plain -q 2>&1 | Out-Null
$exe = Join-Path $compose "build\compose\binaries\main\app\LegadoDesktop\LegadoDesktop.exe"
if ($LASTEXITCODE -eq 0 -and (Test-Path $exe)) { ok "createDistributable -> $exe" } else { bad "package failed" }
Pop-Location

Write-Host "== 3. backend + frontend launch smoke =="
$home = Join-Path $env:TEMP "legado-frontend-smoke"
Remove-Item -Recurse -Force $home -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $home | Out-Null
$env:LEGADO_DESKTOP_HOME = $home
Push-Location $backend
$server = Start-Process -FilePath ".\build\install\legado-desktop-backend\bin\legado-desktop-backend.bat" -PassThru -WindowStyle Hidden
Pop-Location
$ready = $false
for ($i = 0; $i -lt 12; $i++) {
    Start-Sleep -Seconds 2
    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:2323/api/health" -UseBasicParsing -TimeoutSec 2
        if ($r.StatusCode -eq 200) { $ready = $true; break }
    } catch { }
}
if ($ready) { ok "backend ready (health 200)" } else { bad "backend not ready" }

if ($ready -and (Test-Path $exe)) {
    $app = Start-Process -FilePath $exe -PassThru
    Start-Sleep -Seconds 8
    if (-not $app.HasExited) {
        ok "frontend launched and alive (pid=$($app.Id))"
        Stop-Process -Id $app.Id -Force -ErrorAction SilentlyContinue
    } else {
        bad "frontend exited on startup (exit=$($app.ExitCode))"
    }
}

Get-Process -Name "legado-desktop-backend" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Remove-Item Env:LEGADO_DESKTOP_HOME -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "========== frontend self-check summary =========="
Write-Host "  PASS: $PASS"
Write-Host "  FAIL: $FAIL"
exit $FAIL
