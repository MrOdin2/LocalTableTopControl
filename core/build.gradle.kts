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
