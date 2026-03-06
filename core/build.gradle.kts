plugins {
    kotlin("jvm")
    id("org.openjfx.javafxplugin")
    application
}

rootProject.subprojects
    .filter { it.name != "core" }
    .forEach { evaluationDependsOn(it.path) }

val pluginModules = rootProject.subprojects
    .filter { it.name != "core" }
    .flatMap {
        @Suppress("UNCHECKED_CAST")
        (it.findProperty("requiredJavafxModules") as? List<String>) ?: emptyList()
    }

javafx {
    version = "21"
    modules = (listOf("javafx.controls", "javafx.fxml", "javafx.graphics", "javafx.base") + pluginModules).distinct()
}

application {
    mainClass.set("com.tabletopcontrol.core.AppKt")
}

dependencies {
    implementation(kotlin("stdlib"))
    runtimeOnly(project(":map"))
    runtimeOnly(project(":audio"))
    runtimeOnly(project(":light"))
    runtimeOnly(project(":tracker"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.mockk:mockk:1.13.10")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

val javaVersion = 17

kotlin {
    jvmToolchain(javaVersion)
}

tasks.register<Exec>("jpackage") {
    val installDistTask = tasks.named<Sync>("installDist")
    dependsOn(installDistTask)

    // Keep as Providers so they are resolved inside the task action, not at configuration time
    val distDir = installDistTask.map { it.destinationDir }
    val outputDir = layout.buildDirectory.dir("jpackage")

    // Register inputs/outputs for up-to-date checks
    inputs.dir(distDir)
    outputs.dir(outputDir)

    // Accept overrides from -PpkgType=... and -PpkgVersion=... on the CLI
    val pkgType = project.findProperty("pkgType")?.toString() ?: "app-image"
    val pkgVersionProp = project.findProperty("pkgVersion")?.toString()
    val pkgVersion = if (!pkgVersionProp.isNullOrBlank()) {
        pkgVersionProp
    } else {
        val rawVersion = project.version.toString().replace("-SNAPSHOT", "")
        val versionMatch = Regex("^[0-9]+(\\.[0-9]+)*").find(rawVersion)
        versionMatch?.value?.ifEmpty { "1.0.0" } ?: "1.0.0"
    }

    // Resolve jpackage from the configured Java toolchain
    val javaToolchains = project.extensions.getByType<JavaToolchainService>()
    val javaLauncher = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }

    // Resolve the actual jar file name from the Jar task to avoid drift from project.version
    val mainJarName = tasks.named<org.gradle.jvm.tasks.Jar>("jar").flatMap { it.archiveFileName }

    doFirst {
        val distDirFile = distDir.get()
        val outputDirFile = outputDir.get().asFile
        val javaHome = javaLauncher.get().metadata.installationPath.asFile
        val mainJar = mainJarName.get()

        outputDirFile.deleteRecursively()
        outputDirFile.mkdirs()

        commandLine(
            "${javaHome.absolutePath}/bin/jpackage",
            "--type", pkgType,
            "--name", "TabletopControl",
            "--app-version", pkgVersion,
            "--input", "${distDirFile.absolutePath}/lib",
            "--main-jar", mainJar,
            "--main-class", "com.tabletopcontrol.core.AppKt",
            "--dest", outputDirFile.absolutePath,
            "--description", "TabletopControl",
            "--vendor", "MrOdin"
        )
    }
}

