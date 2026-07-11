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
            commandLine("npm", "ci")
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
            commandLine(parts)
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
    commandArgs.set(listOf("npx", "ng", "build", "--configuration=production", "--no-emit"))
}

tasks.register<ExecNpx>("webTest") {
    description = "Run Angular tests"
    dependsOn("npmCi")
    commandArgs.set(listOf("npx", "ng", "test", "--watch=false", "--browsers=ChromeHeadless"))
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
