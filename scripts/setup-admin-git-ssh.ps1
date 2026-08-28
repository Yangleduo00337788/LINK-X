# Run once as user "yangleduo" (not Admin) to let Admin use the same Git SSH key.
# Usage: powershell -ExecutionPolicy Bypass -File scripts/setup-admin-git-ssh.ps1

$ErrorActionPreference = 'Stop'

$src = Join-Path $env:USERPROFILE '.ssh'
$dst = 'C:\Users\Admin\.ssh'
$files = @('id_rsa', 'id_rsa.pub', 'known_hosts')

if (-not (Test-Path (Join-Path $src 'id_rsa'))) {
    Write-Error "SSH private key not found at $src\id_rsa. Run this script as yangleduo."
}

New-Item -ItemType Directory -Path $dst -Force | Out-Null

foreach ($name in $files) {
    $from = Join-Path $src $name
    if (Test-Path $from) {
        Copy-Item $from (Join-Path $dst $name) -Force
        Write-Host "Copied $name"
    }
}

# OpenSSH on Windows requires the private key to be readable only by the using account.
icacls (Join-Path $dst 'id_rsa') /inheritance:r /grant:r 'yangleduo\Admin:(R)' 'SYSTEM:(F)' | Out-Null
icacls (Join-Path $dst 'id_rsa.pub') /inheritance:r /grant:r 'yangleduo\Admin:(R)' 'SYSTEM:(F)' | Out-Null

$config = @'
Host gitee.com github.com
    User git
    IdentityFile ~/.ssh/id_rsa
    IdentitiesOnly yes
'@

Set-Content -Path (Join-Path $dst 'config') -Value $config -Encoding utf8NoBOM
Write-Host "Admin SSH config written to $dst\config"
Write-Host "Done. Switch to Admin and run: ssh -T git@gitee.com"
