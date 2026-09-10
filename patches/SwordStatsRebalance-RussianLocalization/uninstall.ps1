#Requires -Version 7
<#
.SYNOPSIS
    Restores the original (pre-patch) Sword Stats Rebalance templates from the
    backup made by install.ps1.
.PARAMETER GameRoot
    The Witcher Enhanced Edition install root. Defaults to the common
    C:\Games\The Witcher Enhanced Edition.
.PARAMETER BackupDir
    Optional explicit backup directory. Defaults to the oldest (first)
    *-russian-localization backup under <GameRoot>\Mod Conflict Backups.
#>
param(
    [string]$GameRoot = "C:\Games\The Witcher Enhanced Edition",
    [string]$BackupDir = ""
)

$ErrorActionPreference = 'Stop'

$dataRoot = Join-Path $GameRoot 'Data'
$modRoot = Join-Path $dataRoot 'Override\Swords Stats Rebalance'
$backupRoot = Join-Path $GameRoot 'Mod Conflict Backups\Swords Stats Rebalance'

if ($BackupDir -eq '') {
    $candidates = @(Get-ChildItem -LiteralPath $backupRoot -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name.EndsWith('russian-localization') } |
        Sort-Object Name)
    if ($candidates.Count -eq 0) {
        throw "No russian-localization backup found under '$backupRoot'."
    }
    $BackupDir = $candidates[0].FullName
}

if (-not (Test-Path -LiteralPath $BackupDir)) {
    throw "Backup directory '$BackupDir' does not exist."
}

$backupFiles = @(Get-ChildItem -LiteralPath $BackupDir -Recurse -Filter '*.uti' | ForEach-Object {
    [PSCustomObject]@{
        Backup = $_
        Target = Join-Path $modRoot $_.FullName.Substring($BackupDir.Length + 1)
    }
})

if ($backupFiles.Count -ne 39) {
    throw "Expected 39 backed-up templates, found $($backupFiles.Count) in '$BackupDir' - backup is incomplete."
}

function Get-Sha256([string]$Path) {
    (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
}

foreach ($entry in $backupFiles) {
    if (-not (Test-Path -LiteralPath $entry.Target)) {
        throw "Refusing to restore - target '$($entry.Target)' is missing."
    }
    Copy-Item -LiteralPath $entry.Backup.FullName -Destination $entry.Target -Force
    if ((Get-Sha256 $entry.Target) -ne (Get-Sha256 $entry.Backup.FullName)) {
        throw "Verification failed for $($entry.Backup.Name) - restored file does not match the backup."
    }
}

Write-Host "Restored $($backupFiles.Count) original template(s) from: $BackupDir"
Write-Host "The backup directory was kept."
