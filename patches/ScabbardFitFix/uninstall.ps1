#Requires -Version 7
param(
    [string]$GameRoot = 'C:\Games\The Witcher Enhanced Edition'
)

$ErrorActionPreference = 'Stop'

$target = Join-Path $GameRoot 'Data\z_zSwords_DM\zzz_scabbards\steel\Witcher\ph_stl_001.mdb'
$wfxTarget = Join-Path $GameRoot 'Data\z_zSwords_DM\zzz_scabbards\wfx\fx_stl001_pa1.wfx'
$backupRoot = Join-Path $GameRoot 'Mod Conflict Backups\Scabbard Fit Fix'
$backup = Join-Path $backupRoot 'ph_stl_001.mdb.original'
$wfxBackup = Join-Path $backupRoot 'fx_stl001_pa1.wfx.original'
$expectedSourceSha256 = '53E6A0A9196D1F8A474ABFBF4E586067951BB0BB8BA0454BD97D26C4499B6BF1'
$expectedWfxSha256 = '42AB8E529B7D8BC2D0A301637CCBAD98FE8C8A9EDE2C8BFBAEA3C7FA6540B8AF'
$obsoleteOverrideRoot = Join-Path $GameRoot 'Data\zzzz_scabbard_fit_fix'
$obsoleteOverride = Join-Path $obsoleteOverrideRoot 'ph_stl_001.mdb'

if (-not (Test-Path -LiteralPath $backup)) {
    throw "Verified original backup is missing: '$backup'."
}

if ((Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash -ne $expectedSourceSha256) {
    throw "Backup '$backup' is not the expected original model; refusing to restore it."
}
if (-not (Test-Path -LiteralPath $wfxBackup)) {
    throw "Verified original placement backup is missing: '$wfxBackup'."
}
if ((Get-FileHash -LiteralPath $wfxBackup -Algorithm SHA256).Hash -ne $expectedWfxSha256) {
    throw "Backup '$wfxBackup' is not the expected original placement effect; refusing to restore it."
}

Copy-Item -LiteralPath $backup -Destination $target -Force
Copy-Item -LiteralPath $wfxBackup -Destination $wfxTarget -Force
if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $expectedSourceSha256) {
    throw "Verification failed while restoring '$target'."
}
if ((Get-FileHash -LiteralPath $wfxTarget -Algorithm SHA256).Hash -ne $expectedWfxSha256) {
    throw "Verification failed while restoring '$wfxTarget'."
}

if (Test-Path -LiteralPath $obsoleteOverride) {
    $unexpected = @(Get-ChildItem -LiteralPath $obsoleteOverrideRoot -Force |
        Where-Object { $_.FullName -ne $obsoleteOverride })
    if ($unexpected.Count -eq 0) {
        Remove-Item -LiteralPath $obsoleteOverride
        Remove-Item -LiteralPath $obsoleteOverrideRoot
    }
}

Write-Host "Restored the original scabbard model: $target"
Write-Host "Restored the original back placement: $wfxTarget"
Write-Host "Backup retained: $backup"
