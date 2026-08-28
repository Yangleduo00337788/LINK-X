# Run as user "yangleduo" (has SSH key at C:\Users\yangleduo\.ssh\id_rsa)
# Usage: powershell -ExecutionPolicy Bypass -File scripts/push-master-remotes.ps1

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$srcKey = Join-Path $env:USERPROFILE '.ssh\id_rsa'
if (-not (Test-Path $srcKey)) {
    Write-Error "SSH key not found at $srcKey. Run this script as yangleduo."
}

# Copy key to Admin profile so Cursor/Admin shell can also use git SSH later.
$adminSsh = 'C:\Users\Admin\.ssh'
if ($env:USERPROFILE -ne 'C:\Users\yangleduo') {
    Write-Host "Current user: $env:USERPROFILE (expected C:\Users\yangleduo)"
}
if (Test-Path $adminSsh) {
    foreach ($name in @('id_rsa', 'id_rsa.pub', 'known_hosts')) {
        $from = Join-Path $env:USERPROFILE '.ssh' $name
        if (Test-Path $from) {
            Copy-Item $from (Join-Path $adminSsh $name) -Force
        }
    }
    $config = @'
Host gitee.com github.com
    User git
    IdentityFile ~/.ssh/id_rsa
    IdentitiesOnly yes
'@
    Set-Content -Path (Join-Path $adminSsh 'config') -Value $config -Encoding utf8NoBOM
    icacls (Join-Path $adminSsh 'id_rsa') /inheritance:r /grant:r 'yangleduo\Admin:(R)' 'SYSTEM:(F)' | Out-Null
    icacls (Join-Path $adminSsh 'id_rsa.pub') /inheritance:r /grant:r 'yangleduo\Admin:(R)' 'SYSTEM:(F)' | Out-Null
    Write-Host 'Copied SSH keys to Admin profile.'
}

Write-Host 'Pushing master to Gitee (origin)...'
git push origin master
Write-Host 'Pushing master to GitHub (github)...'
git push github master
Write-Host 'Done.'
