#Requires -Version 7
param(
    [string]$GameRoot = 'C:\Games\The Witcher Enhanced Edition',
    [string]$SourceRoot = (Join-Path $PSScriptRoot '..\..\mods\unpacked\TW3 scabbards witcher scools\viper')
)

$ErrorActionPreference = 'Stop'

if (Get-Process -Name witcher -ErrorAction SilentlyContinue) {
    throw 'Exit the game before installing the Viper scabbard variant.'
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

foreach ($file in $payload) {
    $source = Join-Path $SourceRoot $file.Path
    if (-not (Test-Path -LiteralPath $source)) {
        throw "Required Viper source file is missing: '$source'."
    }
    $sourceHash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
    if ($sourceHash -ne $file.Sha256) {
        throw "Unexpected Viper source file '$source'. Expected SHA-256 $($file.Sha256), found $sourceHash."
    }
}

if (-not (Test-Path -LiteralPath $manifestPath)) {
    New-Item -ItemType Directory -Force -Path $backupFilesRoot | Out-Null
    $entries = foreach ($file in $payload) {
        $target = Join-Path $scabbardRoot $file.Path
        $existed = Test-Path -LiteralPath $target
        $targetHash = $null
        if ($existed) {
            $targetHash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
            $backup = Join-Path $backupFilesRoot $file.Path
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $backup) | Out-Null
            Copy-Item -LiteralPath $target -Destination $backup -Force
            if ((Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash -ne $targetHash) {
                throw "Backup verification failed for '$target'."
            }
        }
        [PSCustomObject]@{
            RelativePath = $file.Path
            Existed = $existed
            Sha256 = $targetHash
        }
    }

    $manifest = [PSCustomObject]@{
        SchemaVersion = 1
        CreatedAtUtc = [DateTime]::UtcNow.ToString('o')
        Entries = @($entries)
    }
    $temporaryManifest = "$manifestPath.tmp"
    [IO.File]::WriteAllText($temporaryManifest, ($manifest | ConvertTo-Json -Depth 4), [Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temporaryManifest -Destination $manifestPath -Force
}

$savedManifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
if ($savedManifest.SchemaVersion -ne 1 -or $savedManifest.Entries.Count -ne $payload.Count) {
    throw "Unsupported or incomplete backup manifest: '$manifestPath'."
}
foreach ($file in $payload) {
    $entry = @($savedManifest.Entries | Where-Object RelativePath -eq $file.Path)
    if ($entry.Count -ne 1) {
        throw "Backup manifest does not contain exactly one entry for '$($file.Path)'."
    }
    if ($entry[0].Existed) {
        $backup = Join-Path $backupFilesRoot $file.Path
        if (-not (Test-Path -LiteralPath $backup)) {
            throw "Recorded backup is missing: '$backup'."
        }
        if ((Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash -ne $entry[0].Sha256) {
            throw "Recorded backup has changed: '$backup'."
        }
    }
}

foreach ($file in $payload) {
    $source = Join-Path $SourceRoot $file.Path
    $target = Join-Path $scabbardRoot $file.Path
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    $temporaryTarget = "$target.viper-installing"
    Copy-Item -LiteralPath $source -Destination $temporaryTarget -Force
    if ((Get-FileHash -LiteralPath $temporaryTarget -Algorithm SHA256).Hash -ne $file.Sha256) {
        throw "Staged-copy verification failed for '$target'."
    }
    Move-Item -LiteralPath $temporaryTarget -Destination $target -Force
}

foreach ($file in $payload) {
    $target = Join-Path $scabbardRoot $file.Path
    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $file.Sha256) {
        throw "Installed-file verification failed for '$target'."
    }
}

Write-Host "Installed and verified $($payload.Count) Viper scabbard files."
Write-Host "Pre-install state retained at: $backupRoot"
Write-Host 'Run uninstall.ps1 to restore that exact state.'
