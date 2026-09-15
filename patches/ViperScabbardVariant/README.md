# Viper scabbard variant

Reversible installer for the Viper option from Nexus mod 1193, "TW3
Scabbards Witcher Scools". It installs the selected `viper` payload into the
existing TW3 CS Swords and Scabbards resource tree.

The variant replaces the shared Witcher steel and silver `.mdb/.mdl` models
and adds five textures. It does not modify scripts, WFX placement files, 2DA
tables, item templates, armor resources, or save data.

## Install

Exit the game and run from this directory:

```powershell
pwsh -File install.ps1
```

The installer verifies all nine downloaded files, records every destination's
pre-install state, and verifies every installed copy. The default source is the
unpacked archive under the repository's ignored `mods` directory.

The exact pre-install state is retained under:

```text
<GameRoot>\Mod Conflict Backups\Viper Scabbard Variant\
```

## Uninstall

```powershell
pwsh -File uninstall.ps1
```

The uninstaller validates the complete backup before changing anything. It
restores files that existed before installation and removes files that were
introduced by Viper only when they still match the known Viper hashes.

The Viper models replace the models used by `SteelScabbardFitFix` and
`SilverScabbardFitFix`. Their geometry and mesh names differ, so the existing
fit installers must not be run against them. If Viper needs alignment changes,
use the Viper-specific depth tuner in this package.

## Tune scabbard depth

Exit the game. Specify either scabbard or both in one invocation:

```powershell
pwsh -File tune-depth.ps1 -SteelDepthDegrees -1.0 -SilverDepthDegrees -0.6
```

To adjust only one without changing the other:

```powershell
pwsh -File tune-depth.ps1 -SilverDepthDegrees -0.5
```

Every specified model is regenerated from the verified downloaded Viper MDB,
so values are absolute and repeated adjustments do not accumulate. Passing `0`
restores that Viper MDB's unrotated geometry. The script changes only vertex
positions and mesh bounding boxes; the sword models and WFX placement files
remain untouched.
