pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
		mavenLocal()
	}
}

dependencyResolutionManagement {
	repositories {
		mavenCentral()
		mavenLocal()
	}
}

rootProject.name = "kmcp"
include("kmcp-compiler")
include("kmcp-ksp")
