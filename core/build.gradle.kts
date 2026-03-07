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
        versionMatch?.value ?: "1.0.0"
    }

    // Resolve jpackage from the configured Java toolchain
    val javaToolchains = project.extensions.getByType<JavaToolchainService>()
    val javaLauncher = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }

    // Resolve the jar file and its name from the Jar task; wires the task dependency via inputs
    val jarTask = tasks.named<org.gradle.jvm.tasks.Jar>("jar")
    inputs.file(jarTask.flatMap { it.archiveFile })
    val mainJarName = jarTask.flatMap { it.archiveFileName }

    // Wire --input as a proper Provider to avoid hard-coded /lib suffix
    val libDir = distDir.map { File(it, "lib") }

    doFirst {
        // Defer provider resolution to execution time to avoid eager toolchain lookup on every build
        val javaHome = javaLauncher.get().metadata.installationPath.asFile
        val jpackageExt = if (System.getProperty("os.name").lowercase().contains("win")) ".exe" else ""

        executable = javaHome.resolve("bin").resolve("jpackage$jpackageExt").absolutePath

        args(
            "--type", pkgType,
            "--name", "TabletopControl",
            "--app-version", pkgVersion,
            "--input", libDir.get().absolutePath,
            "--main-jar", mainJarName.get(),
            "--main-class", "com.tabletopcontrol.core.AppKt",
            "--dest", outputDir.get().asFile.absolutePath,
            "--description", "TabletopControl",
            "--vendor", "MrOdin"
        )
    }
}

