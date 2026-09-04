# Compile production code with a Java 25 floor, not Java 8

The build originally kept `--release 8` so the plain cross-platform JAR would run on the Java 8 runtime the old README advertised. The fork's primary artifact is now the self-contained app image with a bundled Temurin 25 runtime (ADR-0002), and the Java 8 story only served the secondary plain-JAR path. We decided to raise the production language floor to **Java 25** (`--release 25`): production code can use modern Java (records, switch expressions, `var`, `Files` APIs), and the Kotlin compilation target moves in lockstep. The plain JAR still works cross-platform — but only on a modern JRE, and the README no longer advertises Java 8.

**Considered options**: staying at 8 (maximum compatibility for the plain JAR, but blocks every language improvement and contradicts the bundled-runtime direction); floor 21 (wider compatibility for the JAR path at the cost of the newest syntax).

**Consequences**: anyone running the raw JAR needs a current JRE download; CI and the bundled runtime already run 25, so no environment work. Reversing later is trivial (a build-file number), but the decision matters for every line of code written on top of it.
