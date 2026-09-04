import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    application
    alias(libs.plugins.kotlin.jvm)
}

version = "4.0.0-SNAPSHOT"

application {
    mainClass = "app.tweditor.Main"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.compileJava {
    options.release = 8
}

dependencies {
    implementation(libs.flatlaf)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to application.mainClass.get(),
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}

val runtimeImageDir = layout.buildDirectory.dir("runtime-image")
val appImageDir = layout.buildDirectory.dir("app-image")
val jpackageInputDir = layout.buildDirectory.dir("jpackage-input")
val appImageDest = layout.buildDirectory.dir("distributions")

val packageVersion = version.toString().removeSuffix("-SNAPSHOT")

val toolchainJdkHome = javaToolchains.launcherFor {
    languageVersion = java.toolchain.languageVersion
    vendor = java.toolchain.vendor
}.map { it.metadata.installationPath }

val jlinkRuntime = tasks.register<Exec>("jlinkRuntime") {
    group = "distribution"
    description = "Builds a module-trimmed Java runtime (java.base + java.desktop, per jdeps on the app jar)."
    dependsOn(tasks.jar)
    outputs.dir(runtimeImageDir)
    val runtime = runtimeImageDir.get().asFile
    doFirst {
        runtime.deleteRecursively()
    }
    commandLine(
        toolchainJdkHome.get().file("bin/jlink.exe").asFile.absolutePath,
        "--add-modules", "java.base,java.desktop",
        "--strip-debug",
        "--no-header-files",
        "--no-man-pages",
        "--compress", "zip-6",
        "--output", runtime.absolutePath
    )
}

tasks.register<Exec>("jpackageAppImage") {
    group = "distribution"
    description = "Builds a self-contained Windows app-image via jpackage with the trimmed runtime and the app icon."
    dependsOn(jlinkRuntime)
    inputs.files(tasks.jar)
    outputs.dir(appImageDir)
    val inputDir = jpackageInputDir.get().asFile
    val image = appImageDir.get().asFile
    doFirst {
        inputDir.deleteRecursively()
        inputDir.mkdirs()
        tasks.jar.get().archiveFile.get().asFile.copyTo(
            inputDir.resolve(tasks.jar.get().archiveFileName.get()), overwrite = true)
        image.deleteRecursively()
    }
    commandLine(
        toolchainJdkHome.get().file("bin/jpackage.exe").asFile.absolutePath,
        "--type", "app-image",
        "--input", inputDir.absolutePath,
        "--main-jar", tasks.jar.get().archiveFileName.get(),
        "--main-class", "app.tweditor.Main",
        "--name", "TWEditor",
        "--app-version", packageVersion,
        "--vendor", "AlekseiTovkachev",
        "--icon", file("res/TWEditor.ico").absolutePath,
                "--java-options", "-Xmx256m",
                "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--runtime-image", runtimeImageDir.get().asFile.absolutePath,
        "--dest", image.absolutePath
    )
}

tasks.register<Zip>("zipAppImage") {
    group = "distribution"
    description = "Zips the jpackage app-image into the distributable artifact."
    dependsOn(tasks.named("jpackageAppImage"))
    from(appImageDir)
    archiveBaseName = "TWEditor"
    archiveAppendix = "win"
    archiveVersion = packageVersion
    destinationDirectory = appImageDest
}

tasks.register("packageWindowsAppImage") {
    group = "distribution"
    description = "Produces the zipped, self-contained Windows app-image (single entry point)."
    dependsOn(tasks.named("zipAppImage"))
}
