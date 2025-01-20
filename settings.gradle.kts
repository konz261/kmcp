pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}
}

dependencyResolutionManagement {
	repositories {
		mavenCentral()
	}
}

rootProject.name = "mcp4k"
include("mcp4k-compiler")
include("mcp4k-gradle")
include("mcp4k-ksp")
include("mcp4k-runtime")

includeBuild("mcp4k-build") {
	dependencySubstitution {
		substitute(module("sh.ondr.mcp4k:mcp4k-gradle")).using(project(":gradle-plugin"))
	}
}
