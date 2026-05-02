rootProject.name = "tabletopcontrol"

include("core", "map", "audio", "light", "dynamicmap", "dynamicmap_builder")
project(":dynamicmap_builder").projectDir = file("dynamicmap_builder")

include("tracker")
include("canvas")
