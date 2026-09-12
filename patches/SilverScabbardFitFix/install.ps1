#Requires -Version 7
<#
.SYNOPSIS
    Installs the accepted Witcher silver-scabbard alignment correction.
.DESCRIPTION
    Regenerates the high-priority TW3 CS silver scabbard from a verified
    original, applying two rigid rotations around each visual mesh's mouth.
    Only vertex positions and mesh bounding boxes are rewritten.
.PARAMETER GameRoot
    The Witcher Enhanced Edition installation root.
.PARAMETER PlanarDegrees
    Model-local Y rotation controlling the shoulder-to-shoulder angle.
    Negative values rotate clockwise in the owner's rear-view test.
.PARAMETER DepthDegrees
    Model-local X rotation controlling inward/outward depth tilt.
#>
param(
    [string]$GameRoot = 'C:\Games\The Witcher Enhanced Edition',
    [ValidateRange(-10.0, 10.0)]
    [double]$PlanarDegrees = -0.45,
    [ValidateRange(-10.0, 10.0)]
    [double]$DepthDegrees = -0.6
)

$ErrorActionPreference = 'Stop'

if (Get-Process -Name witcher -ErrorAction SilentlyContinue) {
    throw 'Exit the game before installing the silver scabbard fit correction.'
}

$target = Join-Path $GameRoot 'Data\z_zSwords_DM\zzz_scabbards\silver\Witcher\ph_sv_001.mdb'
$backupRoot = Join-Path $GameRoot 'Mod Conflict Backups\Silver Scabbard Fit Fix'
$backup = Join-Path $backupRoot 'ph_sv_001.mdb.original'
$expectedSourceSha256 = '35F252B77E7FE92C19EAC98C3D088A0600787A5E5F0D364CF196CE9C7E282537'
$meshNames = @('scabbard_01.001', 'scabbard_01.002', 'shadow.001')

if (-not (Test-Path -LiteralPath $target)) {
    throw "Required TW3 CS silver scabbard is missing: '$target'."
}

$currentSha256 = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
if (Test-Path -LiteralPath $backup) {
    if ((Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash -ne $expectedSourceSha256) {
        throw "Backup '$backup' is not the expected original model; refusing to continue."
    }
}
elseif ($currentSha256 -eq $expectedSourceSha256) {
    New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
    Copy-Item -LiteralPath $target -Destination $backup
    if ((Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash -ne $expectedSourceSha256) {
        throw "Backup verification failed for '$backup'."
    }
}
else {
    throw "Unexpected model at '$target' and no verified original backup exists. Expected SHA-256 $expectedSourceSha256, found $currentSha256."
}

$bytes = [IO.File]::ReadAllBytes($backup)
$original = [byte[]]$bytes.Clone()
$modelDataOffset = 32
$fileVersion = [BitConverter]::ToUInt32($bytes, 4) -band 0x0fffffff
if ($fileVersion -ne 136) {
    throw "Unsupported MDB version $fileVersion; expected Witcher MDB version 136."
}

function Get-UInt32([int]$Offset) { [BitConverter]::ToUInt32($bytes, $Offset) }
function Get-Float32([int]$Offset) { [BitConverter]::ToSingle($bytes, $Offset) }
function Set-Float32([int]$Offset, [single]$Value) { [BitConverter]::GetBytes($Value).CopyTo($bytes, $Offset) }
function Get-NodeName([int]$Offset) {
    $raw = [Text.Encoding]::ASCII.GetString($bytes, $Offset, 64)
    $terminator = $raw.IndexOf([char]0)
    if ($terminator -ge 0) { return $raw.Substring(0, $terminator) }
    return $raw
}

$nodes = @{}
$visited = @{}
function Read-Node([uint32]$RelativeOffset) {
    if ($visited.ContainsKey($RelativeOffset)) { return }
    $visited[$RelativeOffset] = $true
    $nodeOffset = $modelDataOffset + [int]$RelativeOffset
    $nodes[(Get-NodeName ($nodeOffset + 32))] = [PSCustomObject]@{
        Offset = $nodeOffset
        Type = Get-UInt32 ($nodeOffset + 160)
    }
    $childrenOffset = Get-UInt32 ($nodeOffset + 104)
    $childrenCount = Get-UInt32 ($nodeOffset + 108)
    for ($index = 0; $index -lt $childrenCount; $index++) {
        Read-Node (Get-UInt32 ($modelDataOffset + [int]$childrenOffset + 4 * $index))
    }
}
Read-Node (Get-UInt32 104)

$depthRadians = $DepthDegrees * [Math]::PI / 180.0
$planarRadians = $PlanarDegrees * [Math]::PI / 180.0
$cosDepth = [Math]::Cos($depthRadians)
$sinDepth = [Math]::Sin($depthRadians)
$cosPlanar = [Math]::Cos($planarRadians)
$sinPlanar = [Math]::Sin($planarRadians)
$allowedOffsets = [Collections.Generic.HashSet[int]]::new()
$patched = @()

foreach ($meshName in $meshNames) {
    $node = $nodes[$meshName]
    if ($null -eq $node -or $node.Type -ne 0x21) {
        throw "Expected trimesh '$meshName' was not found in the source model."
    }

    $arraysOffset = $modelDataOffset + [int](Get-UInt32 ($node.Offset + 172))
    $verticesOffset = $modelDataOffset + [int](Get-UInt32 ($arraysOffset + 4))
    $vertexCount = Get-UInt32 ($arraysOffset + 8)
    if ($vertexCount -eq 0) { throw "Trimesh '$meshName' has no vertices." }

    $mouthZ = [double]::PositiveInfinity
    for ($index = 0; $index -lt $vertexCount; $index++) {
        $z = Get-Float32 ($verticesOffset + 12 * $index + 8)
        if ($z -lt $mouthZ) { $mouthZ = $z }
    }

    $mins = @([double]::PositiveInfinity, [double]::PositiveInfinity, [double]::PositiveInfinity)
    $maxs = @([double]::NegativeInfinity, [double]::NegativeInfinity, [double]::NegativeInfinity)
    for ($index = 0; $index -lt $vertexCount; $index++) {
        $offset = $verticesOffset + 12 * $index
        $x = [double](Get-Float32 $offset)
        $y = [double](Get-Float32 ($offset + 4))
        $relativeZ = [double](Get-Float32 ($offset + 8)) - $mouthZ

        # Depth rotation around local X, then back-plane rotation around local Y.
        $depthY = $y * $cosDepth - $relativeZ * $sinDepth
        $depthZ = $y * $sinDepth + $relativeZ * $cosDepth
        $planarX = $x * $cosPlanar + $depthZ * $sinPlanar
        $planarZ = -$x * $sinPlanar + $depthZ * $cosPlanar
        $values = @([single]$planarX, [single]$depthY, [single]($mouthZ + $planarZ))

        for ($axis = 0; $axis -lt 3; $axis++) {
            Set-Float32 ($offset + 4 * $axis) $values[$axis]
            for ($byte = 0; $byte -lt 4; $byte++) {
                [void]$allowedOffsets.Add($offset + 4 * $axis + $byte)
            }
            if ($values[$axis] -lt $mins[$axis]) { $mins[$axis] = $values[$axis] }
            if ($values[$axis] -gt $maxs[$axis]) { $maxs[$axis] = $values[$axis] }
        }
    }

    for ($axis = 0; $axis -lt 3; $axis++) {
        $minimumOffset = $node.Offset + 180 + 4 * $axis
        $maximumOffset = $node.Offset + 192 + 4 * $axis
        Set-Float32 $minimumOffset ([single]$mins[$axis])
        Set-Float32 $maximumOffset ([single]$maxs[$axis])
        for ($byte = 0; $byte -lt 4; $byte++) {
            [void]$allowedOffsets.Add($minimumOffset + $byte)
            [void]$allowedOffsets.Add($maximumOffset + $byte)
        }
    }
    $patched += "$meshName ($vertexCount vertices, mouth Z $mouthZ)"
}

$changedByteCount = 0
$outsideAllowedCount = 0
for ($index = 0; $index -lt $bytes.Length; $index++) {
    if ($bytes[$index] -ne $original[$index]) {
        $changedByteCount++
        if (-not $allowedOffsets.Contains($index)) { $outsideAllowedCount++ }
    }
}
if ($outsideAllowedCount -ne 0) {
    throw "$outsideAllowedCount bytes changed outside vertex and bounding-box regions."
}

$generatedSha256 = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))
if ($currentSha256 -eq $generatedSha256) {
    Write-Host "Silver scabbard fit correction is already installed: $target"
}
else {
    $temporaryTarget = "$target.tmp"
    [IO.File]::WriteAllBytes($temporaryTarget, $bytes)
    Move-Item -LiteralPath $temporaryTarget -Destination $target -Force
}
if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $generatedSha256) {
    throw 'Verification failed: installed model differs from the generated model.'
}

Write-Host "Verified original backup: $backup"
Write-Host "Planar mouth rotation: $PlanarDegrees degrees"
Write-Host "Depth mouth rotation: $DepthDegrees degrees"
Write-Host "Changed bytes: $changedByteCount; outside allowed regions: $outsideAllowedCount"
$patched | ForEach-Object { Write-Host "  $_" }
