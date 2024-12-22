plugins {
	alias(libs.plugins.build.config)
}

allprojects {
	version = "kmcp-internal"

	repositories {
		mavenCentral()
		gradlePluginPortal()
	}
}
