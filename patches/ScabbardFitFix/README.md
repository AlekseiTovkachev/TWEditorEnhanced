# Scabbard fit fix

Reversible alignment correction for the Witcher/meteorite steel scabbard supplied by
Complete Sword Overhaul (Nexus mod 1188). The default correction keeps the original
length and placement effect, then rigidly rotates the sheath vertices `-1.25°` around
model-local X at the handle-side mouth. This moves
the lower end along the model-local +Y depth direction confirmed by the prior trial,
without the skewed appearance caused by progressively shearing its cross-sections.
It also translates the complete sheath `-0.005` model units along local X to center it
on the sword.

The small rotation intentionally leaves the packed tangent/binormal buffers untouched.
Those buffers overlap in this MDB and treating them as independent float arrays corrupts
the model; an earlier experimental build did so and was immediately reverted after it
caused save loading to crash.

Optional length tuning remains available and is anchored at the mouth of every visual
LOD mesh, so the scabbard still meets the sword at the guard. A Process Monitor trace
showed that the game reads this exact model:

```text
Data\z_zSwords_DM\zzz_scabbards\steel\Witcher\ph_stl_001.mdb
```

The installer preserves the verified model and `fx_stl001_pa1.wfx` originals under
`<GameRoot>\Mod Conflict Backups\Scabbard Fit Fix\` before patching it in place. An
earlier experiment using a separately named override directory did not win the game's
resource collision and is removed automatically.

## Requirements

- The Witcher: Enhanced Edition.
- Complete Sword Overhaul's Witcher sword/scabbard files installed under
  `Data\z_zSwords_DM\zzz_scabbards\steel\Witcher`.

The installer accepts only the known mod-1188 source model (SHA-256
`53E6A0A9196D1F8A474ABFBF4E586067951BB0BB8BA0454BD97D26C4499B6BF1`) and fails before
writing if a different model is present.

## Install

```powershell
pwsh -File install.ps1
```

The defaults are the original `1.0` longitudinal scale, a `-1.25°` rigid mouth
rotation, and a `-0.005` local-X centering offset. They can be tuned independently,
for example with `-TiltDegrees -1.2 -CenterOffsetX -0.004`.

Fully exit the game before installing, then start it and load the Save again so the
model resource and attached visual effect are recreated.

## Uninstall

```powershell
pwsh -File uninstall.ps1
```

Uninstall restores both digest-verified originals and keeps the backups.
