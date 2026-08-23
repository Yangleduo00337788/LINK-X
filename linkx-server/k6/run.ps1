param(
    [ValidateSet('smoke', 'auth-load', 'chat-read', 'mixed')]
    [string]$Scenario = 'smoke',
    [string]$Username = $env:K6_USERNAME,
    [string]$Password = $env:K6_PASSWORD,
    [string]$BaseUrl = $env:K6_BASE_URL,
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
    Write-Error '未找到 k6，请先安装: https://k6.io/docs/get-started/installation/'
}

$prepScript = Join-Path $here 'prep-k6-load.py'
if ((Test-Path $prepScript) -and (Get-Command python -ErrorAction SilentlyContinue)) {
    Write-Host 'Preparing rate-limit whitelist for k6...' -ForegroundColor DarkGray
    & python $prepScript
    if ($LASTEXITCODE -ne 0) {
        Write-Warning 'prep-k6-load.py 未完全成功，压测可能触发 429 限流'
    }
}

$envArgs = @()
if ($BaseUrl) { $envArgs += '-e', "K6_BASE_URL=$BaseUrl" }
if ($Username) { $envArgs += '-e', "K6_USERNAME=$Username" }
if ($Password) { $envArgs += '-e', "K6_PASSWORD=$Password" }

if ($Scenario -ne 'smoke' -and (-not $Username -or -not $Password)) {
    Write-Error '非 smoke 场景需要 k6.env 或 -Username / -Password 或环境变量 K6_USERNAME / K6_PASSWORD'
}

$summaryArgs = @()
if ($ExportSummary) {
    $resultsDir = Join-Path $here 'results'
    if (-not (Test-Path $resultsDir)) { New-Item -ItemType Directory -Path $resultsDir | Out-Null }
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $summaryArgs += '--summary-export', (Join-Path $resultsDir "$Scenario-$stamp.json")
}

Write-Host "Running k6 scenario: $Scenario.js" -ForegroundColor Cyan
& k6 run @summaryArgs @envArgs "$Scenario.js"
exit $LASTEXITCODE
