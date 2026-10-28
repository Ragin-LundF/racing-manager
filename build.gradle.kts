plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

group = "io.github.ragin-lundf"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
    }
}

tasks.register("cleanAll") {
    dependsOn(
        gradle.includedBuilds.map { it.task(":clean") } +
            subprojects.map { it.tasks.named("clean") },
    )
}

tasks.register("buildAll") {
    dependsOn(
        subprojects.map { it.tasks.named("build") },
    )
}

tasks.register("checkAll") {
    dependsOn(
        subprojects.map { it.tasks.named("check") },
    )
}

tasks.register("verifyAll") {
    dependsOn(
        tasks.named("checkAll"),
    )
    description = "Run all verification tasks across all subprojects"
}

tasks.register("assembleDistribution") {
    dependsOn(
        subprojects.map { it.tasks.named("assemble") },
    )
    description = "Assemble all artifacts for distribution"
}
