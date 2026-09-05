$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

Write-Host "======================================" -ForegroundColor Green
Write-Host "       KRISHINIRNAY STARTING" -ForegroundColor Green
Write-Host "======================================" -ForegroundColor Green

Write-Host ""
Write-Host "Checking Android device..." -ForegroundColor Yellow

& $ADB devices

Write-Host ""
Write-Host "Setting Android -> FastAPI connection..." -ForegroundColor Yellow

& $ADB reverse tcp:8000 tcp:8000

Write-Host ""
Write-Host "ADB Reverse:" -ForegroundColor Green

& $ADB reverse --list

Write-Host ""
Write-Host "Starting FastAPI..." -ForegroundColor Yellow

Set-Location "$PSScriptRoot\server"

uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload