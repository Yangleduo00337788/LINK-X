param(
    [ValidateSet('smoke', 'load', 'soak', 'stress')]
    [string]$Profile = 'stress',
    [int]$Vus = 0,
    [string]$Duration = '',
    [switch]$ExportSummary,
    [switch]$SkipPrep
)

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$logDir = Join-Path $here 'results'
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }
$logFile = Join-Path $logDir "social-suite-$stamp.log"

if (-not $SkipPrep) {
    Write-Host 'Preparing social fixtures (group/conference/moments/workspace)...' -ForegroundColor DarkGray
    & python (Join-Path $here 'prep-k6-social.py')
    if ($LASTEXITCODE -ne 0) {
        Write-Error 'prep-k6-social.py failed'
    }
}

$scenarios = @(
    'group-load',
    'group-write',
    'group-extra',
    'conference-load',
    'cloud-load',
    'moments-load',
    'moments-publish',
    'friend-load',
    'short-video-interact',
    'workspace-load'
)
$results = @()

Write-Host "=== LinkX k6 social suite $stamp profile=$Profile vus=$Vus duration=$Duration ===" -ForegroundColor Cyan
Write-Host "log: $logFile"

foreach ($scenario in $scenarios) {
    $sep = "`n========== $scenario ==========`n"
    Add-Content -Path $logFile -Value $sep
    Write-Host $sep -ForegroundColor Yellow

    $runParams = @{ Scenario = $scenario; Profile = $Profile }
    if ($Vus -gt 0) { $runParams['Vus'] = $Vus }
    if ($Duration) { $runParams['Duration'] = $Duration }
    if ($ExportSummary) { $runParams['ExportSummary'] = $true }

    $output = & (Join-Path $here 'run.ps1') @runParams 2>&1
    $output | Tee-Object -FilePath $logFile -Append
    $code = $LASTEXITCODE
    $results += [pscustomobject]@{ Scenario = $scenario; ExitCode = $code; Pass = ($code -eq 0) }
    Start-Sleep -Seconds 8
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
