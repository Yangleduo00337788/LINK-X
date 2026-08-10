# 作者：yangleduo
# 清理 linkx-client 全部打包产物与中间目录
$ErrorActionPreference = 'Continue'
$root = Split-Path $PSScriptRoot -Parent

Get-Process 'LinkX Installer', LinkX, electron -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 1

$empty = Join-Path $root '.empty-dir'
New-Item -ItemType Directory -Force -Path $empty | Out-Null

$targets = @(
  'release',
  'release-build',
  '.installer-payload',
  'dist',
  'dist-electron',
  'dist-installer',
  'dist-installer-electron',
  '_asar_extract',
  '.tmp-handle'
)

foreach ($name in $targets) {
  $target = Join-Path $root $name
  if (-not (Test-Path $target)) { continue }
  robocopy $empty $target /MIR /NFL /NDL /NJH /NJS /nc /ns /np | Out-Null
  cmd /c "rmdir /s /q `"$target`"" 2>$null
  if (Test-Path $target) {
    Write-Host "[clean:release] 未能删除 $name（可能被 Cursor/杀毒占用）"
    Write-Host "请完全退出 Cursor 后，在外部 PowerShell 再执行: npm run clean:release"
  } else {
    Write-Host "[clean:release] 已删除 $name"
  }
}

Remove-Item $empty -Force -ErrorAction SilentlyContinue
Write-Host "[clean:release] 完成"
