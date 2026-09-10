import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kover)
}

version = "4.1.0-SNAPSHOT"

compose.desktop {
    application {
        mainClass = "app.tweditor.Main"
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "TWEditor"
            packageVersion = version.toString().removeSuffix("-SNAPSHOT")
            modules("java.desktop")
            windows {
                iconFile.set(project.file("res/TWEditor.ico"))
            }
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Kover is intentionally report-only.  Coverage is evidence for the mutation
// families documented in the issue, not a gate with an arbitrary global
// percentage threshold.
tasks.register("coverageReport") {
    group = "verification"
    description = "Writes the report-only Kover coverage artifacts."
    dependsOn("koverHtmlReport", "koverXmlReport")
}

tasks.test {
    useJUnitPlatform()
    // Tests that inspect owner-installed saves or game resources are opt-in;
    // the committed fixtures above are the release-gating test surface.
    useJUnitPlatform {
        excludeTags("local")
    }
    systemProperty("tweditor.screenshots", System.getProperty("tweditor.screenshots"))
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.register<Test>("localSaveTest") {
    group = "verification"
    description = "Runs opt-in tests against owner-installed game resources and .local-saves files."
    dependsOn(tasks.testClasses)
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    useJUnitPlatform {
        includeTags("local")
    }
    systemProperty("tweditor.screenshots", System.getProperty("tweditor.screenshots"))
    testLogging {
        events("passed", "skipped", "failed")
    }
}

val appImageDir = layout.buildDirectory.dir("app-image")
tasks.register<Copy>("packageWindowsAppImage") {
    group = "distribution"
    description = "Builds the self-contained Windows Compose app image."
    dependsOn(tasks.named("createDistributable"))
    doFirst {
        val destination = appImageDir.get().dir("TWEditor").asFile
        if (destination.exists()) {
            destination.walkBottomUp().forEach {
                it.setWritable(true)
            }
            destination.deleteRecursively()
        }
    }
    from(layout.buildDirectory.dir("compose/binaries/main/app/TWEditor"))
    into(appImageDir.map { it.dir("TWEditor") })
}
