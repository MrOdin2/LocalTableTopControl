rootProject.name = "tabletopcontrol"

include("core", "map", "audio", "light", "dynamicmap_builder")
project(":dynamicmap_builder").projectDir = file("dynamicmap_builder")

include("tracker")
