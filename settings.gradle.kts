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

rootProject.name = "kmcp"
include("kmcp-compiler")
include("kmcp-gradle")
include("kmcp-ksp")
include("kmcp-runtime")

includeBuild("kmcp-build") {
	dependencySubstitution {
		substitute(module("sh.ondr.kmcp:kmcp-gradle")).using(project(":gradle-plugin"))
	}
}
