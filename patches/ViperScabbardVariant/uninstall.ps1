#Requires -Version 7
param(
    [string]$GameRoot = 'C:\Games\The Witcher Enhanced Edition'
)

$ErrorActionPreference = 'Stop'

if (Get-Process -Name witcher -ErrorAction SilentlyContinue) {
    throw 'Exit the game before uninstalling the Viper scabbard variant.'
}

$payload = @(
    [PSCustomObject]@{ Path = 'silver\Witcher\ph_1_4_d01.tga'; Sha256 = '8EFDAEEB29F37143F5081DA57C3C4D3C283DCAECF8707C15E9170328873CE1DD' }
    [PSCustomObject]@{ Path = 'silver\Witcher\ph_1_4_n01.tga'; Sha256 = 'EEEAADF1F06B822F9ECE4E228EC5D17441B840A74CC923E6D3399B6E1331B717' }
    [PSCustomObject]@{ Path = 'silver\Witcher\ph_sv_001.mdb'; Sha256 = 'E83105A4D8030DE2BDD43FD4D2FC9510A8F4B483D7446380A241762639CDBB33' }
    [PSCustomObject]@{ Path = 'silver\Witcher\ph_sv_001.mdl'; Sha256 = 'BC3821210A0C95AFE338EA6FC1D562F02427C50BC38CC4DAE376F6690FD73D23' }
    [PSCustomObject]@{ Path = 'steel\Witcher\ph_1_2_d01.tga'; Sha256 = '9A9DFE60490960A150201B572A54480CCF2F216A42B97F349591023AE8BB0F50' }
    [PSCustomObject]@{ Path = 'steel\Witcher\ph_1_2_d02.tga'; Sha256 = '7F3F101DC7D3891C09332329F161C970BB1AAEA94125047025F9CCCD6DD9118C' }
    [PSCustomObject]@{ Path = 'steel\Witcher\ph_1_2_n01.tga'; Sha256 = '3927514DA30AAB55F4C624DDEF7D1CD513A647F8E39C052EC86C505D86B976B2' }
    [PSCustomObject]@{ Path = 'steel\Witcher\ph_stl_001.mdb'; Sha256 = 'AA2E3D00E3292E5E5D8E4582834F7C48CA1E9C081F5D38E5C7FFFE64847EBD48' }
    [PSCustomObject]@{ Path = 'steel\Witcher\ph_stl_001.mdl'; Sha256 = '1F1F173A3851113137CD74076EAC01AB48383EA3C7A2AC982E47BC74391DC850' }
)

$scabbardRoot = Join-Path $GameRoot 'Data\z_zSwords_DM\zzz_scabbards'
$backupRoot = Join-Path $GameRoot 'Mod Conflict Backups\Viper Scabbard Variant'
$backupFilesRoot = Join-Path $backupRoot 'files'
$manifestPath = Join-Path $backupRoot 'manifest.json'

if (-not (Test-Path -LiteralPath $manifestPath)) {
    throw "Backup manifest is missing: '$manifestPath'."
}
$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
if ($manifest.SchemaVersion -ne 1 -or $manifest.Entries.Count -ne $payload.Count) {
    throw "Unsupported or incomplete backup manifest: '$manifestPath'."
}

# Validate the entire restore before changing any target.
foreach ($file in $payload) {
    $entry = @($manifest.Entries | Where-Object RelativePath -eq $file.Path)
    if ($entry.Count -ne 1) {
        throw "Backup manifest does not contain exactly one entry for '$($file.Path)'."
    }
    $target = Join-Path $scabbardRoot $file.Path
    if ($entry[0].Existed) {
        $backup = Join-Path $backupFilesRoot $file.Path
        if (-not (Test-Path -LiteralPath $backup)) {
            throw "Recorded backup is missing: '$backup'."
        }
        if ((Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash -ne $entry[0].Sha256) {
            throw "Recorded backup has changed: '$backup'."
        }
    }
    elseif ((Test-Path -LiteralPath $target) -and
            (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $file.Sha256) {
        throw "Viper-added file '$target' has since changed; refusing to delete it."
    }
}

foreach ($file in $payload) {
    $entry = @($manifest.Entries | Where-Object RelativePath -eq $file.Path)[0]
    $target = Join-Path $scabbardRoot $file.Path
    if ($entry.Existed) {
        $backup = Join-Path $backupFilesRoot $file.Path
        $temporaryTarget = "$target.viper-restoring"
        Copy-Item -LiteralPath $backup -Destination $temporaryTarget -Force
        Move-Item -LiteralPath $temporaryTarget -Destination $target -Force
        if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $entry.Sha256) {
            throw "Restore verification failed for '$target'."
        }
    }
    elseif (Test-Path -LiteralPath $target) {
        Remove-Item -LiteralPath $target -Force
    }
}

Write-Host 'Removed the Viper scabbard variant and restored the exact pre-install state.'
Write-Host "Backup retained at: $backupRoot"
