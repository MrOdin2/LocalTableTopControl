plugins {
    kotlin("jvm") version "2.1.0" apply false
    id("org.openjfx.javafxplugin") version "0.1.0" apply false
}

allprojects {
    group = "com.tabletopcontrol"
    version = "1.2.0" // THIS IS OUR VERSION NUMBER

    repositories {
        mavenCentral()
    }
}
