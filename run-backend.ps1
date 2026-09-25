param(
    [int]$Port = 9090
)

$ErrorActionPreference = "Stop"

Write-Host "=== ANVEX Backend (headless) ==="
$jar = Join-Path $PSScriptRoot 'target\anvex-1.0-SNAPSHOT.jar'
if (-not (Test-Path $jar)) { throw "Backend JAR missing. Run mvn package first." }
Push-Location $PSScriptRoot
try {
    Write-Host "Starting ANVEX backend on port $Port (Ctrl+C to stop)..."
    java -jar $jar $Port
} finally { Pop-Location }
