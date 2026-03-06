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

kotlin {
    jvmToolchain(17)
}

tasks.register<Exec>("jpackage") {
    dependsOn(tasks.named("installDist"))

    val distDir = layout.buildDirectory.dir("install/core").get().asFile
    val outputDir = layout.buildDirectory.dir("jpackage").get().asFile

    // Accept overrides from -PpkgType=... and -PpkgVersion=... on the CLI
    val pkgType = project.findProperty("pkgType")?.toString() ?: "app-image"
    val pkgVersion = project.findProperty("pkgVersion")?.toString()
        ?: project.version.toString().replace("-SNAPSHOT", "").ifEmpty { "1.0.0" }

    doFirst {
        outputDir.deleteRecursively()
        outputDir.mkdirs()
    }

    commandLine(
        "${System.getProperty("java.home")}/bin/jpackage",
        "--type", pkgType,
        "--name", "TabletopControl",
        "--app-version", pkgVersion,
        "--input", "$distDir/lib",
        "--main-jar", "core-${project.version}.jar",
        "--main-class", "com.tabletopcontrol.core.AppKt",
        "--dest", outputDir.absolutePath,
        "--description", "TabletopControl",
        "--vendor", "MrOdin"
    )
}

