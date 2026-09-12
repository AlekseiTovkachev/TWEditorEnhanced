#Requires -Version 7
<#
.SYNOPSIS
    Installs a reversible fit correction for the Witcher/meteorite steel scabbard.
.DESCRIPTION
    Backs up and patches the exact Complete Sword Overhaul model loaded by the
    game. It can scale its three visual LOD meshes along local Z and rigidly rotate
    them around the mouth. A Process Monitor trace proved that a separate later-named resource
    root does not win this resref collision.
.PARAMETER GameRoot
    The Witcher Enhanced Edition install root.
.PARAMETER Stretch
    Longitudinal scale applied from the sheath mouth. Defaults to 1.0 (original length).
.PARAMETER TiltDegrees
    Rigid local-X rotation around the sheath mouth. Defaults to -1.25 degrees, moving
    the lower end in the model-local +Y direction confirmed by the prior trial.
.PARAMETER CenterOffsetX
    Uniform model-local X translation used to center the sheath on the sword.
    Defaults to -0.005 model units (five millimeters toward screen-left from the rear).
#>
param(
    [string]$GameRoot = 'C:\Games\The Witcher Enhanced Edition',
    [ValidateRange(0.8, 1.25)]
    [double]$Stretch = 1.0,
    [ValidateRange(-10.0, 10.0)]
    [double]$TiltDegrees = -1.25,
    [ValidateRange(-0.05, 0.05)]
    [double]$CenterOffsetX = -0.005
)

$ErrorActionPreference = 'Stop'

$dataRoot = Join-Path $GameRoot 'Data'
$target = Join-Path $dataRoot 'z_zSwords_DM\zzz_scabbards\steel\Witcher\ph_stl_001.mdb'
$wfxTarget = Join-Path $dataRoot 'z_zSwords_DM\zzz_scabbards\wfx\fx_stl001_pa1.wfx'
$backupRoot = Join-Path $GameRoot 'Mod Conflict Backups\Scabbard Fit Fix'
$backup = Join-Path $backupRoot 'ph_stl_001.mdb.original'
$wfxBackup = Join-Path $backupRoot 'fx_stl001_pa1.wfx.original'
$obsoleteOverrideRoot = Join-Path $dataRoot 'zzzz_scabbard_fit_fix'
$obsoleteOverride = Join-Path $obsoleteOverrideRoot 'ph_stl_001.mdb'
$expectedSourceSha256 = '53E6A0A9196D1F8A474ABFBF4E586067951BB0BB8BA0454BD97D26C4499B6BF1'
$expectedWfxSha256 = '42AB8E529B7D8BC2D0A301637CCBAD98FE8C8A9EDE2C8BFBAEA3C7FA6540B8AF'
$meshNames = @('shadow', 'cutscene', 'cutscene.001')
$tiltRadians = $TiltDegrees * [Math]::PI / 180.0
$cosTilt = [Math]::Cos($tiltRadians)
$sinTilt = [Math]::Sin($tiltRadians)

if (-not (Test-Path -LiteralPath $target)) {
    throw "Required Complete Sword Overhaul model is missing: '$target'."
}
if (-not (Test-Path -LiteralPath $wfxTarget)) {
    throw "Required traced back-placement effect is missing: '$wfxTarget'."
}

$currentSha256 = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
if (Test-Path -LiteralPath $backup) {
    $backupSha256 = (Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash
    if ($backupSha256 -ne $expectedSourceSha256) {
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
    throw "Unexpected model version at '$target' and no verified original backup exists. Expected SHA-256 $expectedSourceSha256, found $currentSha256."
}

$currentWfxSha256 = (Get-FileHash -LiteralPath $wfxTarget -Algorithm SHA256).Hash
if (Test-Path -LiteralPath $wfxBackup) {
    if ((Get-FileHash -LiteralPath $wfxBackup -Algorithm SHA256).Hash -ne $expectedWfxSha256) {
        throw "Backup '$wfxBackup' is not the expected original placement effect; refusing to continue."
    }
}
elseif ($currentWfxSha256 -eq $expectedWfxSha256) {
    New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
    Copy-Item -LiteralPath $wfxTarget -Destination $wfxBackup
    if ((Get-FileHash -LiteralPath $wfxBackup -Algorithm SHA256).Hash -ne $expectedWfxSha256) {
        throw "Backup verification failed for '$wfxBackup'."
    }
}
else {
    throw "Unexpected placement effect at '$wfxTarget' and no verified original backup exists. Expected SHA-256 $expectedWfxSha256, found $currentWfxSha256."
}

$bytes = [IO.File]::ReadAllBytes($backup)
$modelDataOffset = 32
$fileVersion = [BitConverter]::ToUInt32($bytes, 4) -band 0x0fffffff
if ($fileVersion -ne 136) {
    throw "Unsupported MDB version $fileVersion; expected Witcher MDB version 136."
}

function Get-UInt32([int]$Offset) {
    [BitConverter]::ToUInt32($bytes, $Offset)
}

function Get-Float32([int]$Offset) {
    [BitConverter]::ToSingle($bytes, $Offset)
}

function Set-Float32([int]$Offset, [single]$Value) {
    [BitConverter]::GetBytes($Value).CopyTo($bytes, $Offset)
}

function Get-NodeName([int]$Offset) {
    $raw = [Text.Encoding]::ASCII.GetString($bytes, $Offset, 64)
    $terminator = $raw.IndexOf([char]0)
    if ($terminator -ge 0) {
        return $raw.Substring(0, $terminator)
    }
    return $raw
}

$nodes = @{}
$visited = @{}
function Read-Node([uint32]$RelativeOffset) {
    if ($visited.ContainsKey($RelativeOffset)) {
        return
    }
    $visited[$RelativeOffset] = $true

    $nodeOffset = $modelDataOffset + [int]$RelativeOffset
    $name = Get-NodeName ($nodeOffset + 32)
    $nodeType = Get-UInt32 ($nodeOffset + 160)
    $nodes[$name] = [PSCustomObject]@{
        Name = $name
        Offset = $nodeOffset
        Type = $nodeType
    }

    $childrenOffset = Get-UInt32 ($nodeOffset + 104)
    $childrenCount = Get-UInt32 ($nodeOffset + 108)
    for ($index = 0; $index -lt $childrenCount; $index++) {
        $childOffset = Get-UInt32 ($modelDataOffset + [int]$childrenOffset + 4 * $index)
        Read-Node $childOffset
    }
}

$rootNodeOffset = Get-UInt32 104
Read-Node $rootNodeOffset

$patched = @()
foreach ($meshName in $meshNames) {
    $node = $nodes[$meshName]
    if ($null -eq $node -or $node.Type -ne 0x21) {
        throw "Expected trimesh '$meshName' was not found in the source model."
    }

    # A Witcher MDB trimesh stores its mesh-array block pointer at node + 172.
    # The first array definition in that block is the XYZ vertex array.
    $meshArraysOffset = $modelDataOffset + [int](Get-UInt32 ($node.Offset + 172))
    $verticesOffset = $modelDataOffset + [int](Get-UInt32 ($meshArraysOffset + 4))
    $vertexCount = Get-UInt32 ($meshArraysOffset + 8)
    if ($vertexCount -eq 0) {
        throw "Trimesh '$meshName' has no vertices."
    }

    $mouthZ = [double]::PositiveInfinity
    $tipZ = [double]::NegativeInfinity
    for ($index = 0; $index -lt $vertexCount; $index++) {
        $z = Get-Float32 ($verticesOffset + 12 * $index + 8)
        if ($z -lt $mouthZ) {
            $mouthZ = $z
        }
        if ($z -gt $tipZ) {
            $tipZ = $z
        }
    }
    $axisLength = $tipZ - $mouthZ
    if ($axisLength -le 0) {
        throw "Trimesh '$meshName' has an invalid longitudinal range."
    }

    $newMinX = [double]::PositiveInfinity
    $newMaxX = [double]::NegativeInfinity
    $newMinY = [double]::PositiveInfinity
    $newMaxY = [double]::NegativeInfinity
    $newMinZ = [double]::PositiveInfinity
    $newMaxZ = [double]::NegativeInfinity
    for ($index = 0; $index -lt $vertexCount; $index++) {
        $xOffset = $verticesOffset + 12 * $index
        $yOffset = $verticesOffset + 12 * $index + 4
        $zOffset = $verticesOffset + 12 * $index + 8
        $x = Get-Float32 $xOffset
        $y = Get-Float32 $yOffset
        $z = Get-Float32 $zOffset
        $shiftedX = [single]($x + $CenterOffsetX)
        $longitudinalZ = ($z - $mouthZ) * $Stretch
        $shiftedY = [single]($y * $cosTilt - $longitudinalZ * $sinTilt)
        $stretchedZ = [single]($mouthZ + $y * $sinTilt + $longitudinalZ * $cosTilt)
        Set-Float32 $xOffset $shiftedX
        Set-Float32 $yOffset $shiftedY
        Set-Float32 $zOffset $stretchedZ
        if ($shiftedX -lt $newMinX) {
            $newMinX = $shiftedX
        }
        if ($shiftedX -gt $newMaxX) {
            $newMaxX = $shiftedX
        }
        if ($shiftedY -lt $newMinY) {
            $newMinY = $shiftedY
        }
        if ($shiftedY -gt $newMaxY) {
            $newMaxY = $shiftedY
        }
        if ($stretchedZ -lt $newMinZ) {
            $newMinZ = $stretchedZ
        }
        if ($stretchedZ -gt $newMaxZ) {
            $newMaxZ = $stretchedZ
        }
    }

    # Keep the node bounding box consistent with the rewritten vertices.
    Set-Float32 ($node.Offset + 180) ([single]$newMinX)
    Set-Float32 ($node.Offset + 184) ([single]$newMinY)
    Set-Float32 ($node.Offset + 188) ([single]$newMinZ)
    Set-Float32 ($node.Offset + 192) ([single]$newMaxX)
    Set-Float32 ($node.Offset + 196) ([single]$newMaxY)
    Set-Float32 ($node.Offset + 200) ([single]$newMaxZ)
    $patched += "${meshName}: rigid mouth rotation $TiltDegrees degrees, Z $newMinZ -> $newMaxZ ($vertexCount vertices)"
}

$temporaryTarget = "$target.tmp"
[IO.File]::WriteAllBytes($temporaryTarget, $bytes)
$generatedSha256 = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))
$alreadyInstalled = $currentSha256 -eq $generatedSha256
if ($alreadyInstalled) {
    Remove-Item -LiteralPath $temporaryTarget
}
else {
    Move-Item -LiteralPath $temporaryTarget -Destination $target -Force
}

$installedSha256 = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
if ($installedSha256 -ne $generatedSha256) {
    throw "Verification failed: installed model differs from the generated model."
}

$wfxText = [IO.File]::ReadAllText($wfxBackup)
$sourceRotation = '<Property name="Rotation" x="-0.300000" y="180.000000" z="310.000000"></Property>'
$targetRotation = $sourceRotation
if (-not $wfxText.Contains($sourceRotation)) {
    throw "Expected back-placement rotation was not found in '$wfxBackup'."
}
$patchedWfxText = $wfxText.Replace($sourceRotation, $targetRotation)
$utf8NoBom = [Text.UTF8Encoding]::new($false)
$temporaryWfxTarget = "$wfxTarget.tmp"
[IO.File]::WriteAllText($temporaryWfxTarget, $patchedWfxText, $utf8NoBom)
$generatedWfxSha256 = (Get-FileHash -LiteralPath $temporaryWfxTarget -Algorithm SHA256).Hash
$wfxAlreadyInstalled = $currentWfxSha256 -eq $generatedWfxSha256
if ($wfxAlreadyInstalled) {
    Remove-Item -LiteralPath $temporaryWfxTarget
}
else {
    Move-Item -LiteralPath $temporaryWfxTarget -Destination $wfxTarget -Force
}
if ((Get-FileHash -LiteralPath $wfxTarget -Algorithm SHA256).Hash -ne $generatedWfxSha256) {
    throw "Verification failed: installed placement effect differs from the generated effect."
}

if (Test-Path -LiteralPath $obsoleteOverride) {
    $unexpected = @(Get-ChildItem -LiteralPath $obsoleteOverrideRoot -Force |
        Where-Object { $_.FullName -ne $obsoleteOverride })
    if ($unexpected.Count -eq 0) {
        Remove-Item -LiteralPath $obsoleteOverride
        Remove-Item -LiteralPath $obsoleteOverrideRoot
    }
}

if ($alreadyInstalled) {
    Write-Host "Scabbard fit correction is already installed: $target"
}
else {
    Write-Host "Patched the traced scabbard model: $target"
}
Write-Host "Verified original backup: $backup"
Write-Host "Verified placement backup: $wfxBackup"
Write-Host "Longitudinal stretch: $Stretch"
Write-Host "Rigid mouth rotation: $TiltDegrees degrees"
Write-Host "Uniform local-X centering offset: $CenterOffsetX"
$patched | ForEach-Object { Write-Host "  $_" }
