plugins {
	alias(libs.plugins.kotlin.jvm).apply(false)
	alias(libs.plugins.kotlin.multiplatform).apply(false)
	alias(libs.plugins.maven.publish).apply(false)
	alias(libs.plugins.ondrsh.mcp4k).apply(false)
	alias(libs.plugins.spotless)
}

allprojects {
	version = property("VERSION_NAME") as String

	configurations.configureEach {
		resolutionStrategy.dependencySubstitution {
			substitute(module("sh.ondr.mcp4k:mcp4k-compiler"))
				.using(project(":mcp4k-compiler"))
		}
	}
	apply(plugin = "com.diffplug.spotless")

	spotless {
		kotlin {
			target("**/*.kt")
			ktlint()
		}
		kotlinGradle {
			target("**/*.gradle.kts")
			ktlint()
		}
	}
}
