# 全量 k6 压测：smoke → auth-load → chat-read → mixed
param(
    [switch]$ExportSummary
)

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$logDir = Join-Path $here 'results'
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }
$logFile = Join-Path $logDir "full-suite-$stamp.log"

$scenarios = @('smoke', 'auth-load', 'chat-read', 'mixed')
$results = @()

Write-Host "=== LinkX 全量 k6 压测 $stamp ===" -ForegroundColor Cyan
Write-Host "日志: $logFile"

foreach ($scenario in $scenarios) {
    $sep = "`n========== $scenario ==========`n"
    Add-Content -Path $logFile -Value $sep
    Write-Host $sep -ForegroundColor Yellow

    $runParams = @{ Scenario = $scenario }
    if ($ExportSummary) { $runParams['ExportSummary'] = $true }

    $output = & (Join-Path $here 'run.ps1') @runParams 2>&1
    $output | Tee-Object -FilePath $logFile -Append
    $code = $LASTEXITCODE
    $results += [pscustomobject]@{ Scenario = $scenario; ExitCode = $code; Pass = ($code -eq 0) }
}

$summary = @"
`n========== SUMMARY $stamp ==========
$( $results | Format-Table -AutoSize | Out-String )
"@
Add-Content -Path $logFile -Value $summary
Write-Host $summary -ForegroundColor Cyan

if ($results | Where-Object { -not $_.Pass }) {
    exit 1
}
exit 0
