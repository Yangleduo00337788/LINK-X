param(
    [ValidateSet('smoke', 'auth-load', 'chat-read', 'short-video-read', 'client-bootstrap', 'chat-ws', 'mixed', 'chat-history', 'chat-write', 'upload-chat', 'register-load', 'pay-load', 'call-load', 'group-load', 'group-write', 'group-extra', 'conference-load', 'cloud-load', 'moments-load', 'moments-publish', 'friend-load', 'short-video-interact', 'workspace-load')]
    [string]$Scenario = 'smoke',
    [ValidateSet('smoke', 'load', 'soak', 'stress', 'gradient')]
    [string]$Profile = '',
    [string]$Username = $env:K6_USERNAME,
    [string]$Password = $env:K6_PASSWORD,
    [string]$BaseUrl = $env:K6_BASE_URL,
    [int]$Vus = 0,
    [string]$Duration = '',
    [switch]$WsSend,
    [switch]$ExportSummary
)

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $here

$envFile = Join-Path $here 'k6.env'
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#')) { return }
        $idx = $line.IndexOf('=')
        if ($idx -lt 1) { return }
        $name = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()
        if ($name) { Set-Item -Path "Env:$name" -Value $value }
    }
    if (-not $Username) { $Username = $env:K6_USERNAME }
    if (-not $Password) { $Password = $env:K6_PASSWORD }
    if (-not $BaseUrl) { $BaseUrl = $env:K6_BASE_URL }
}

if (-not (Get-Command k6 -ErrorAction SilentlyContinue)) {
    Write-Error 'k6 not found. Install: https://k6.io/docs/get-started/installation/'
}

$rateLimitEnabled = $env:RATE_LIMIT_ENABLED
$k6EnvFile = Join-Path (Split-Path $here -Parent) '.env.k6'
if ((-not $rateLimitEnabled) -and (Test-Path $k6EnvFile)) {
    Get-Content $k6EnvFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -match '^RATE_LIMIT_ENABLED=(.*)$') { $rateLimitEnabled = $Matches[1].Trim() }
    }
}
$prepScript = Join-Path $here 'prep-k6-load.py'
if ($rateLimitEnabled -eq 'false') {
    Write-Host 'RATE_LIMIT_ENABLED=false (k6 profile), skip Redis whitelist prep' -ForegroundColor DarkGray
} elseif ((Test-Path $prepScript) -and (Get-Command python -ErrorAction SilentlyContinue)) {
    Write-Host 'Preparing rate-limit whitelist for k6...' -ForegroundColor DarkGray
    & python $prepScript
    if ($LASTEXITCODE -ne 0) {
        Write-Warning 'prep-k6-load.py failed; requests may get HTTP 429. Start backend with SPRING_PROFILES_ACTIVE=k6.'
    }
}

if ($BaseUrl) { $env:K6_BASE_URL = $BaseUrl }
if ($Username) { $env:K6_USERNAME = $Username }
if ($Password) { $env:K6_PASSWORD = $Password }
if ($Profile) { $env:K6_PROFILE = $Profile }
# k6 原生会把进程环境里的 K6_VUS/K6_DURATION 当成 CLI option，整段覆盖脚本 scenarios。
# 社交套件要用脚本里的 ramping，所以默认清掉；需要瞬时恒定 VU 时再显式 -Vus。
Remove-Item Env:K6_VUS -ErrorAction SilentlyContinue
Remove-Item Env:K6_DURATION -ErrorAction SilentlyContinue
if ($Vus -gt 0) { $env:K6_TARGET_VUS = "$Vus" }
if ($Duration) { $env:K6_HOLD = $Duration }
if ($WsSend) { $env:K6_WS_SEND = '1' }

$envArgs = @()
Get-ChildItem Env: | Where-Object { $_.Name -like 'K6_*' -and $_.Value } | ForEach-Object {
    $envArgs += '-e', "$($_.Name)=$($_.Value)"
}

if ($Scenario -ne 'smoke' -and (-not $Username -or -not $Password)) {
    Write-Error 'Non-smoke scenarios need k6.env or K6_USERNAME / K6_PASSWORD'
}

$summaryArgs = @()
if ($ExportSummary) {
    $resultsDir = Join-Path $here 'results'
    if (-not (Test-Path $resultsDir)) { New-Item -ItemType Directory -Path $resultsDir | Out-Null }
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $summaryFile = Join-Path $resultsDir ($Scenario + '-' + $stamp + '.json')
    $summaryArgs += '--summary-export'
    $summaryArgs += $summaryFile
}

Write-Host "Running k6 scenario: $Scenario.js" -ForegroundColor Cyan
& k6 run --summary-trend-stats "avg,min,med,max,p(90),p(95),p(99)" @summaryArgs @envArgs "$Scenario.js"
exit $LASTEXITCODE
