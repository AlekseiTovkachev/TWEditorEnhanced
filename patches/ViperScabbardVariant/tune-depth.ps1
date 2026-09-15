#Requires -Version 7
<#
.SYNOPSIS
    Tunes the depth angle of the Viper steel and/or silver scabbard.
.DESCRIPTION
    Regenerates each requested MDB from the hash-verified Viper source so
    repeated adjustments are non-cumulative. Only vertex positions and mesh
    bounding boxes are changed. The rotation is anchored at each mesh's
    handle-side mouth.
.EXAMPLE
    pwsh -File tune-depth.ps1 -SteelDepthDegrees -1.0 -SilverDepthDegrees -0.6
.EXAMPLE
    pwsh -File tune-depth.ps1 -SilverDepthDegrees -0.5
#>
param(
    [string]$GameRoot = 'C:\Games\The Witcher Enhanced Edition',
    [string]$SourceRoot = (Join-Path $PSScriptRoot '..\..\mods\unpacked\TW3 scabbards witcher scools\viper'),
    [ValidateRange(-10.0, 10.0)]
    [Nullable[double]]$SteelDepthDegrees,
    [ValidateRange(-10.0, 10.0)]
    [Nullable[double]]$SilverDepthDegrees
)

$ErrorActionPreference = 'Stop'

if (Get-Process -Name witcher -ErrorAction SilentlyContinue) {
    throw 'Exit the game before tuning the Viper scabbards.'
}
if (-not $PSBoundParameters.ContainsKey('SteelDepthDegrees') -and
    -not $PSBoundParameters.ContainsKey('SilverDepthDegrees')) {
    throw 'Specify -SteelDepthDegrees, -SilverDepthDegrees, or both. Use 0 to restore a Viper MDB to its unrotated geometry.'
}

$scabbardRoot = Join-Path $GameRoot 'Data\z_zSwords_DM\zzz_scabbards'
$models = @(
    [PSCustomObject]@{
        Name = 'Steel'
        ParameterName = 'SteelDepthDegrees'
        RelativeMdb = 'steel\Witcher\ph_stl_001.mdb'
        RelativeMdl = 'steel\Witcher\ph_stl_001.mdl'
        SourceSha256 = 'AA2E3D00E3292E5E5D8E4582834F7C48CA1E9C081F5D38E5C7FFFE64847EBD48'
        CompanionSha256 = '1F1F173A3851113137CD74076EAC01AB48383EA3C7A2AC982E47BC74391DC850'
        MeshNames = @('shadow', 'steel_viper', 'steel_viper.001')
    }
    [PSCustomObject]@{
        Name = 'Silver'
        ParameterName = 'SilverDepthDegrees'
        RelativeMdb = 'silver\Witcher\ph_sv_001.mdb'
        RelativeMdl = 'silver\Witcher\ph_sv_001.mdl'
        SourceSha256 = 'E83105A4D8030DE2BDD43FD4D2FC9510A8F4B483D7446380A241762639CDBB33'
        CompanionSha256 = 'BC3821210A0C95AFE338EA6FC1D562F02427C50BC38CC4DAE376F6690FD73D23'
        MeshNames = @('shadow.004', 'silver_lynx', 'silver_lynx.001')
    }
)

function Set-ViperDepth {
    param(
        [Parameter(Mandatory)]$Model,
        [Parameter(Mandatory)][double]$Degrees
    )

    $source = Join-Path $SourceRoot $Model.RelativeMdb
    $target = Join-Path $scabbardRoot $Model.RelativeMdb
    $companion = Join-Path $scabbardRoot $Model.RelativeMdl

    if (-not (Test-Path -LiteralPath $source)) {
        throw "Required Viper source model is missing: '$source'."
    }
    $sourceHash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
    if ($sourceHash -ne $Model.SourceSha256) {
        throw "Unexpected Viper source model '$source'. Expected SHA-256 $($Model.SourceSha256), found $sourceHash."
    }
    if (-not (Test-Path -LiteralPath $companion)) {
        throw "The Viper $($Model.Name.ToLowerInvariant()) companion MDL is not installed: '$companion'."
    }
    $companionHash = (Get-FileHash -LiteralPath $companion -Algorithm SHA256).Hash
    if ($companionHash -ne $Model.CompanionSha256) {
        throw "The installed $($Model.Name.ToLowerInvariant()) MDL is not the expected Viper variant; refusing to mix model sets."
    }

    $bytes = [IO.File]::ReadAllBytes($source)
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

    $radians = $Degrees * [Math]::PI / 180.0
    $cosine = [Math]::Cos($radians)
    $sine = [Math]::Sin($radians)
    $allowedOffsets = [Collections.Generic.HashSet[int]]::new()
    $patched = @()

    foreach ($meshName in $Model.MeshNames) {
        $node = $nodes[$meshName]
        if ($null -eq $node -or $node.Type -ne 0x21) {
            throw "Expected Viper trimesh '$meshName' was not found in '$source'."
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

            $newY = $y * $cosine - $relativeZ * $sine
            $newZ = $y * $sine + $relativeZ * $cosine
            $values = @([single]$x, [single]$newY, [single]($mouthZ + $newZ))

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

    $generatedHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))
    $temporaryTarget = "$target.depth-tuning"
    [IO.File]::WriteAllBytes($temporaryTarget, $bytes)
    Move-Item -LiteralPath $temporaryTarget -Destination $target -Force
    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $generatedHash) {
        throw "Verification failed while installing the tuned $($Model.Name.ToLowerInvariant()) MDB."
    }

    Write-Host "$($Model.Name) depth: $Degrees degrees"
    Write-Host "Installed SHA-256: $generatedHash"
    Write-Host "Changed bytes: $changedByteCount; outside allowed regions: $outsideAllowedCount"
    $patched | ForEach-Object { Write-Host "  $_" }
}

foreach ($model in $models) {
    if ($PSBoundParameters.ContainsKey($model.ParameterName)) {
        Set-ViperDepth -Model $model -Degrees ([double]$PSBoundParameters[$model.ParameterName])
    }
}
