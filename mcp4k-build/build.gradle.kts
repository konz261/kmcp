plugins {
	alias(libs.plugins.build.config)
}

allprojects {
	version = "mcp4k-internal"

	repositories {
		mavenCentral()
		gradlePluginPortal()
	}
}
