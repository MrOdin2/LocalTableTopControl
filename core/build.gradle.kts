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

// Bundle all user documentation HTML files into the JAR under "userdocs/".
// The directory structure mirrors the relative links between the files so that
// HelpManager can extract them to disk and the browser can resolve inter-file hrefs.
tasks.processResources {
    from(rootProject.file("docs")) { into("userdocs/docs") }
    listOf("map", "audio", "tracker", "light").forEach { module ->
        from(rootProject.file("$module/UserDoc.html")) { into("userdocs/$module") }
    }
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

    // Declare pkgType and pkgVersion as task inputs so CLI changes trigger re-execution
    inputs.property("pkgType", pkgType)
    inputs.property("pkgVersion", pkgVersion)

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

    // Evaluated at configuration time so it can gate both task inputs and execution-time args
    val isWindows = System.getProperty("os.name").lowercase().startsWith("windows")

    doFirst {
        // Defer provider resolution to execution time to avoid eager toolchain lookup on every build
        val jpackageExt = if (isWindows) ".exe" else ""

        val javaHome = javaLauncher.get().metadata.installationPath.asFile
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

        // Windows MSI-specific options for a proper installer experience.
        // Fail fast with a clear message when pkgType=msi is requested on a non-Windows host,
        // rather than letting jpackage emit an opaque error about unsupported options.
        if (pkgType == "msi") {
            check(isWindows) {
                "pkgType=msi is only supported on Windows. Run the jpackage task on a Windows host to build the MSI installer."
            }
            args(
                // Show a directory-chooser dialog so users can pick the install location
                "--win-dir-chooser",
                // Create a desktop shortcut so the app is easy to find after install
                "--win-shortcut",
                // Add a Start Menu shortcut under the TabletopControl group
                "--win-menu",
                "--win-menu-group", "TabletopControl",
                // Stable upgrade UUID prevents every build being treated as a new product
                "--win-upgrade-uuid", "03551855-19E7-4604-926D-1FF368622403"
            )
        }
    }
}

