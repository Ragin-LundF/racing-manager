plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    alias(libs.plugins.detekt)
    application
}

group = rootProject.group
version = rootProject.version

application {
    mainClass = "io.github.raginlundf.racingmanager.ApplicationKt"
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    // Ktor server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)

    // Kotlinx
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // Persistence
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.json)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.liquibase.core)
    implementation(libs.sqlite.jdbc)
    implementation(libs.mariadb.jdbc)
    implementation(libs.hikaricp)

    // Security
    implementation(libs.bcrypt)

    // Logging
    implementation(libs.kotlin.logging)
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j2)

    // Test
    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

detekt {
    config.setFrom(rootProject.projectDir.resolve("config/detekt.yml"))
    buildUponDefaultConfig = true
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    reports {
        html.required = true
        sarif.required = true
    }
}

// Copy Angular build output into backend resources for embedded serving
val webAppBuildDir = rootProject.project(":racingmanager-webapp").projectDir.resolve("dist/racingmanager-webapp/browser")

tasks.named<Copy>("processResources") {
    dependsOn(":racingmanager-webapp:webBuild")
    from(webAppBuildDir) {
        into("webapp")
    }
}

// Fat JAR with all dependencies
tasks.register<Jar>("fatJar") {
    dependsOn(tasks.named("processResources"), tasks.named("compileKotlin"))
    archiveClassifier = "fat"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "Main-Class" to application.mainClass,
            "Implementation-Title" to rootProject.name,
            "Implementation-Version" to rootProject.version,
        )
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
    }
    from(tasks.named("compileKotlin").map { it.outputs })
    from(tasks.named("processResources").map { it.outputs })
}

// jpackage native installer
val jpackageOutputDir = layout.buildDirectory.dir("jpackage").get().asFile

tasks.register<Exec>("jpackageImage") {
    dependsOn("fatJar")
    val fatJar = tasks.named("fatJar").get().outputs.files.singleFile
    val inputDir = layout.buildDirectory.dir("jpackage-input").get().asFile
    doFirst {
        inputDir.mkdirs()
        fatJar.copyTo(inputDir.resolve(fatJar.name), overwrite = true)
    }
    commandLine(
        "jpackage",
        "--type", "app-image",
        "--input", inputDir.absolutePath,
        "--dest", jpackageOutputDir.absolutePath,
        "--name", "RacingManager",
        "--app-version", version.toString().removeSuffix("-SNAPSHOT"),
        "--main-jar", fatJar.name,
        "--main-class", application.mainClass,
        "--java-options", "-Dracingmanager.profile=prod",
        "--vendor", "Ragin Lundf",
        "--description", "PineCar Race Timer - Event management and race timing system",
        "--copyright", "Copyright 2026 Ragin Lundf",
    )
}

tasks.register<Exec>("jpackageInstaller") {
    dependsOn("fatJar")
    val fatJar = tasks.named("fatJar").get().outputs.files.singleFile
    val inputDir = layout.buildDirectory.dir("jpackage-input").get().asFile
    doFirst {
        inputDir.mkdirs()
        fatJar.copyTo(inputDir.resolve(fatJar.name), overwrite = true)
    }
    commandLine(
        "jpackage",
        "--type", getInstallerType(),
        "--input", inputDir.absolutePath,
        "--dest", jpackageOutputDir.absolutePath,
        "--name", "RacingManager",
        "--app-version", version.toString().removeSuffix("-SNAPSHOT"),
        "--main-jar", fatJar.name,
        "--main-class", application.mainClass,
        "--java-options", "-Dracingmanager.profile=prod",
        "--vendor", "Ragin Lundf",
        "--description", "PineCar Race Timer - Event management and race timing system",
        "--copyright", "Copyright 2026 Ragin Lundf",
        "--license-file", rootProject.projectDir.resolve("LICENSE").let {
            if (it.exists()) it.absolutePath else ""
        },
        "--win-menu",
        "--win-shortcut",
        "--win-dir-chooser",
        "--win-per-user-install",
        "--file-associations", rootProject.projectDir.resolve("package/file-associations.properties").let {
            if (it.exists()) it.absolutePath else ""
        },
    )
    isIgnoreExitValue = true
}

fun getInstallerType(): String {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("win") -> "msi"
        os.contains("mac") -> "dmg"
        os.contains("linux") -> "deb"
        else -> "app-image"
    }
}

// Portable ZIP distribution
tasks.register<Zip>("portableZip") {
    dependsOn("fatJar")
    archiveFileName = "RacingManager-${version}-portable.zip"
    destinationDirectory = layout.buildDirectory.dir("distributions")
    from(tasks.named("fatJar").map { it.outputs }) {
        into("RacingManager/lib")
    }
    from(rootProject.projectDir.resolve("package/portable")) {
        into("RacingManager")
        include("**/*")
    }
    // Add a launcher script
    from(rootProject.projectDir.resolve("package/portable/racingmanager.sh")) {
        into("RacingManager")
        filePermissions {
            unix("rwxr-xr-x")
        }
    }
}
