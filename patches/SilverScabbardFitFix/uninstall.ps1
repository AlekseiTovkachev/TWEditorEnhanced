#Requires -Version 7
param(
    [string]$GameRoot = 'C:\Games\The Witcher Enhanced Edition'
)

$ErrorActionPreference = 'Stop'

if (Get-Process -Name witcher -ErrorAction SilentlyContinue) {
    throw 'Exit the game before uninstalling the silver scabbard fit correction.'
}

$target = Join-Path $GameRoot 'Data\z_zSwords_DM\zzz_scabbards\silver\Witcher\ph_sv_001.mdb'
$backup = Join-Path $GameRoot 'Mod Conflict Backups\Silver Scabbard Fit Fix\ph_sv_001.mdb.original'
$expectedSourceSha256 = '35F252B77E7FE92C19EAC98C3D088A0600787A5E5F0D364CF196CE9C7E282537'

if (-not (Test-Path -LiteralPath $backup)) {
    throw "Verified original backup is missing: '$backup'."
}
if ((Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash -ne $expectedSourceSha256) {
    throw "Backup '$backup' is not the expected original model; refusing to restore it."
}

$targetDirectory = Split-Path -Parent $target
if (-not (Test-Path -LiteralPath $targetDirectory)) {
    throw "Required target directory is missing: '$targetDirectory'."
}
Copy-Item -LiteralPath $backup -Destination $target -Force
if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $expectedSourceSha256) {
    throw "Verification failed while restoring '$target'."
}

Write-Host "Restored the original silver scabbard: $target"
Write-Host "Backup retained: $backup"
