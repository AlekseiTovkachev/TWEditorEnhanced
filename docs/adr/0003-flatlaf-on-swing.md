# Keep Swing, reskin with FlatLaf

The GUI is ~3k lines of tightly-coupled Swing panels, and the fork's core value is safe save-file parsing — a toolkit migration (JavaFX, Compose Multiplatform) would be a full GUI rewrite with high regression risk. We decided to keep Swing and modernize its look with FlatLaf, the de-facto standard modern theme for Swing. A structural GUI overhaul remains possible later but is out of scope for 4.0.

**Consequences**: minimal risk, modern flat appearance; the toolkit stays "legacy but bundled in the JDK" — no new dependency chain.
