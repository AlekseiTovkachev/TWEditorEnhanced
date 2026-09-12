# Silver scabbard fit fix

Reversible alignment correction for the Witcher silver scabbard supplied by
TW3 CS Swords and Scabbards. The accepted defaults rotate each visual mesh
around its handle-side mouth:

- `-0.45°` around model-local Y, correcting the shoulder-to-shoulder angle.
- `-0.6°` around model-local X, correcting the outward depth tilt.

The two rotations affect the scabbard only; the sword model and WFX placement
files remain unchanged. The installer rewrites only vertex positions and mesh
bounding boxes. It deliberately leaves normals and the packed tangent/binormal
data untouched because treating the overlapping buffers as independent arrays
previously produced an MDB that crashed save loading.

The visually verified high-priority resource is:

```text
Data\z_zSwords_DM\zzz_scabbards\silver\Witcher\ph_sv_001.mdb
```

Other `ph_sv_001.mdb` copies exist in the mod stack, but changing this copy
produced the expected in-game response. The accepted installed model has
SHA-256 `2AD7C550285CF0B4662E93FAF1EC90E16A2FF986E9EF190559E785D756902219`.

## Requirements

- The Witcher: Enhanced Edition.
- TW3 CS Swords and Scabbards installed under `Data\z_zSwords_DM`.
- The known source model with SHA-256
  `35F252B77E7FE92C19EAC98C3D088A0600787A5E5F0D364CF196CE9C7E282537`.

## Install

Fully exit the game, then run from this directory:

```powershell
pwsh -File install.ps1
```

The defaults reproduce the accepted fit. Further non-cumulative tuning remains
available, for example:

```powershell
pwsh -File install.ps1 -PlanarDegrees -0.4 -DepthDegrees -0.6
```

Negative planar values rotate clockwise in the rear-view test. Every run starts
from the verified original backup rather than the currently installed output.

## Uninstall

```powershell
pwsh -File uninstall.ps1
```

The original is restored while its verified backup is retained under
`<GameRoot>\Mod Conflict Backups\Silver Scabbard Fit Fix\`.
