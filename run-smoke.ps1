param([int]$Port = 19090)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    mvn -q package
    if ($LASTEXITCODE -ne 0) { throw 'Build or tests failed' }
    $jar = Join-Path $PSScriptRoot 'target\anvex-1.0-SNAPSHOT.jar'
    $classPath = "$jar;$(Join-Path $PSScriptRoot 'target\lib\*')"
    $runDir = Join-Path $PSScriptRoot ('target\smoke-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force $runDir | Out-Null
    $outLog = Join-Path $runDir 'backend.log'
    $errLog = Join-Path $runDir 'backend.err.log'
    $backend = Start-Process java -ArgumentList @('-jar', ('"' + $jar + '"'), "$Port") `
        -WorkingDirectory $runDir -RedirectStandardOutput $outLog -RedirectStandardError $errLog `
        -WindowStyle Hidden -PassThru
    $baseUrl = "http://127.0.0.1:$($Port + 1)/api"
    try {
        $ready = $false
        for ($i=0; $i -lt 40; $i++) {
            Start-Sleep -Milliseconds 250
            if ($backend.HasExited) { throw ('Backend exited: ' + (Get-Content $errLog -Raw -ErrorAction SilentlyContinue)) }
            if ((Get-Content $errLog -Raw -ErrorAction SilentlyContinue) -match 'AuthenticationServer started') {
                $ready = $true; break
            }
        }
        if (-not $ready) { throw 'Backend did not start' }
        $status = Invoke-RestMethod -Uri "$baseUrl/status" -TimeoutSec 10
        if (-not $status.PSObject.Properties['runId']) { throw 'Status API failed' }
        Write-Host "Status: runId=$($status.runId) sources=$($status.trackedSources)"
        Invoke-RestMethod -Uri "$baseUrl/config" -Method Put -Body '{"highDelayMs":100}' | Out-Null
        Invoke-RestMethod -Uri "$baseUrl/config" -Method Put -Body '{"blockCooldownMs":1500}' | Out-Null
        Invoke-RestMethod -Uri "$baseUrl/config" -Method Put -Body '{"reaperIntervalMs":200}' | Out-Null
        $attack = java -cp $classPath com.anvex.tools.SmokeClient 127.0.0.1 $Port '192.168.1.50' 20 100 lab_target wrongpass ATTACKER
        if ($LASTEXITCODE -ne 0 -or ($attack -join ' ') -notmatch 'blocked=([1-9][0-9]*)') { throw 'Attack was not blocked' }
        $legit = java -cp $classPath com.anvex.tools.SmokeClient 127.0.0.1 $Port '192.168.1.99' 1 0 lab_target target123 LEGITIMATE
        if ($LASTEXITCODE -ne 0 -or ($legit -join ' ') -notmatch 'success=1') { throw 'Clean source was affected' }
        Start-Sleep -Seconds 3
        $history = Invoke-RestMethod -Uri "$baseUrl/sources/192.168.1.50/history" -TimeoutSec 10
        $historyTypes = ($history | ForEach-Object { $_.type }) -join ','
        if ($historyTypes -notmatch 'SOURCE_BLOCKED' -or $historyTypes -notmatch 'SECURITY_INCIDENT' `
            -or $historyTypes -notmatch 'SOURCE_RELEASED') { throw 'Persisted attack, incident or release is missing' }
        $blockedEvent = $history | Where-Object { $_.type -eq 'SOURCE_BLOCKED' } | Select-Object -First 1
        if (-not $blockedEvent.evidence -or $blockedEvent.evidence.failedAttempts -lt 1) { throw 'Persisted block decision lacks evidence' }
        Write-Host 'SMOKE PASS: packaged JAR, HTTP API, per-source block, clean login, persisted history, evidence and release.'
    } finally {
        if (-not $backend.HasExited) { Stop-Process -Id $backend.Id -Force -ErrorAction SilentlyContinue }
    }
} finally { Pop-Location }
