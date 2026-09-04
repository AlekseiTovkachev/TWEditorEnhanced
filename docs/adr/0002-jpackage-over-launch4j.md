# jpackage over launch4j for the Windows executable

The repo's build used the launch4j Gradle plugin, which produces a launcher `.exe` that still requires users to have a Java runtime installed (and a Mac packaging plugin we don't need). The fork's goal is a normal, self-contained `.exe`, so we decided to use `jpackage` — the packaging tool that ships inside the JDK itself — with a bundled Temurin 25 runtime, producing a portable app-image folder as the release artifact. The launch4j and macAppBundle plugins are removed.

**Considered options**: keeping launch4j (tiny exe, but contradicts "self-contained" — users must install Java); GraalVM native-image (painful with Swing + reflection, no benefit at this app's size).

**Consequences**: the artifact is larger (~40–60 MB with the bundled JRE), but users need zero Java install. Windows-first; other platforms can be added later with the same tool.
