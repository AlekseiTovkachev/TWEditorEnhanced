#Requires -Version 7
<#
.SYNOPSIS
    Applies Russian localization and Scabbard migration compatibility to Sword Stats Rebalance
    for The Witcher: Enhanced Edition.
.DESCRIPTION
    Overwrites the 39 .uti templates shipped with this patch onto the mod
    directory under <GameRoot>\Data\Override\Swords Stats Rebalance, backing
    up every replaced original first. Idempotent: re-running on an already
    patched install copies nothing. Fails before mutating anything when a
    prerequisite is missing (base mod not installed, missing target file) or
    when a duplicate loose .uti winner exists elsewhere under Data. All 33
    sword templates also carry the Scabbard Mod's one-time migration marker.
.PARAMETER GameRoot
    The Witcher Enhanced Edition install root. Defaults to the common
    C:\Games\The Witcher Enhanced Edition.
#>
param(
    [string]$GameRoot = "C:\Games\The Witcher Enhanced Edition"
)

$ErrorActionPreference = 'Stop'

$dataRoot = Join-Path $GameRoot 'Data'
$modRoot = Join-Path $dataRoot 'Override\Swords Stats Rebalance'
$patchFilesRoot = Join-Path $PSScriptRoot 'files\Override\Swords Stats Rebalance'
$backupRoot = Join-Path $GameRoot 'Mod Conflict Backups\Swords Stats Rebalance'

if (-not (Test-Path -LiteralPath (Join-Path $modRoot 'weapon_abl.lua'))) {
    throw "Sword Stats Rebalance is not installed at '$modRoot' (weapon_abl.lua missing). Install the base mod first: https://www.nexusmods.com/witcher/mods/1101"
}

$patchFiles = @(Get-ChildItem -LiteralPath $patchFilesRoot -Recurse -Filter '*.uti' |
    ForEach-Object {
        [PSCustomObject]@{
            Patch     = $_
            Relative  = $_.FullName.Substring($patchFilesRoot.Length + 1)
            Target    = Join-Path $modRoot $_.FullName.Substring($patchFilesRoot.Length + 1)
        }
    })

if ($patchFiles.Count -ne 39) {
    throw "Expected 39 patch templates, found $($patchFiles.Count) in '$patchFilesRoot' - patch package is incomplete."
}

# Fail-first prerequisite checks: nothing is mutated until every target exists
# and no duplicate loose .uti winner shadows the patch.
$missing = @($patchFiles | Where-Object { -not (Test-Path -LiteralPath $_.Target) })
if ($missing.Count -gt 0) {
    throw "Refusing to patch - the following files are missing from the installed mod (unexpected mod version?):`n$($missing.Relative -join "`n")"
}

foreach ($entry in $patchFiles) {
    $others = @(Get-ChildItem -LiteralPath $dataRoot -Recurse -File -Filter ($entry.Patch.Name) |
        Where-Object { $_.FullName -ne $entry.Target -and $_.Extension -eq '.uti' })
    if ($others.Count -gt 0) {
        throw "Refusing to patch - duplicate loose .uti found for '$($entry.Patch.Name)':`n$($others.FullName -join "`n")`nThe patch must not compete with another copy of the same template."
    }
}

function Get-Sha256([string]$Path) {
    (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
}

$already = @()
$toPatch = @()
foreach ($entry in $patchFiles) {
    if ((Get-Sha256 $entry.Target) -eq (Get-Sha256 $entry.Patch.FullName)) {
        $already += $entry.Relative
    }
    else {
        $toPatch += $entry
    }
}

if ($toPatch.Count -eq 0) {
    Write-Host "All $($patchFiles.Count) templates already carry the localization and compatibility data - nothing to do."
    exit 0
}

$existingBackup = @(Get-ChildItem -LiteralPath $backupRoot -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.Name.EndsWith('russian-localization') } |
    Sort-Object Name | Select-Object -First 1)
if ($existingBackup.Count -gt 0) {
    $backupDir = $existingBackup[0].FullName
}
else {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $backupDir = Join-Path $backupRoot "$stamp-russian-localization"
    New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
}

foreach ($entry in $toPatch) {
    $backupTarget = Join-Path $backupDir $entry.Relative
    if (-not (Test-Path -LiteralPath $backupTarget)) {
        New-Item -ItemType Directory -Force -Path (Split-Path $backupTarget) | Out-Null
        Copy-Item -LiteralPath $entry.Target -Destination $backupTarget
    }
    Copy-Item -LiteralPath $entry.Patch.FullName -Destination $entry.Target -Force
    if ((Get-Sha256 $entry.Target) -ne (Get-Sha256 $entry.Patch.FullName)) {
        throw "Verification failed for $($entry.Relative) - installed file does not match the patch."
    }
}

Write-Host "Patched $($toPatch.Count) template(s) with Russian descriptions and compatibility data ($($already.Count) were already patched)."
Write-Host "Originals backed up to: $backupDir"
Write-Host "Weapon ability data (weapon_abl.lua) was not modified."
