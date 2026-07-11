import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    base
    kotlin("jvm") version "2.4.0" apply false
}

group = rootProject.group
version = rootProject.version

abstract class ExecNpmCi @Inject constructor(
    private val execOps: ExecOperations,
) : DefaultTask() {
    @TaskAction
    fun run() {
        execOps.exec {
            workingDir = project.projectDir
            commandLine("/Users/ragin/.local/bin/npm", "ci")
        }
    }
}

abstract class ExecNpx @Inject constructor(
    private val execOps: ExecOperations,
) : DefaultTask() {
    @get:Input
    abstract val commandArgs: ListProperty<String>

    @TaskAction
    fun run() {
        val parts: List<String> = commandArgs.get()
        execOps.exec {
            workingDir = project.projectDir
            environment("PATH", System.getenv("PATH"))
            // Use full path to npx since Gradle daemon may not inherit the user's PATH
            commandLine("/Users/ragin/.local/bin/npx", *parts.drop(1).toTypedArray())
        }
    }
}

tasks.register<ExecNpmCi>("npmCi") {
    description = "Install npm dependencies"
}

tasks.register<ExecNpx>("webLint") {
    description = "Run Angular linting"
    dependsOn("npmCi")
    commandArgs.set(listOf("npx", "ng", "lint"))
}

tasks.register<ExecNpx>("webTypeCheck") {
    description = "Run Angular type checking"
    dependsOn("npmCi")
    commandArgs.set(listOf("npx", "ng", "build", "--configuration=production"))
}

tasks.register<ExecNpx>("webTest") {
    description = "Run Angular tests"
    dependsOn("npmCi")
    commandArgs.set(listOf("npx", "ng", "test", "--watch=false"))
}

tasks.register<ExecNpx>("webBuild") {
    description = "Build Angular application"
    dependsOn("npmCi")
    commandArgs.set(listOf("npx", "ng", "build", "--configuration=production"))
}

tasks.named("check") {
    dependsOn("webLint", "webTypeCheck", "webTest")
}

tasks.named("build") {
    dependsOn("webBuild")
}
