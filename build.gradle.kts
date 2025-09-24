import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
	alias(libs.plugins.kotlin.jvm).apply(false)
	alias(libs.plugins.kotlin.multiplatform).apply(false)
	alias(libs.plugins.gradle.versions)
	alias(libs.plugins.maven.publish).apply(false)
	alias(libs.plugins.ondrsh.mcp4k).apply(false)
	alias(libs.plugins.spotless)
	alias(libs.plugins.dokka)
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
			targetExclude("**/build/**/*.kt")
			ktlint("1.7.1")
			// Force Unix line endings on all platforms
			lineEndings = com.diffplug.spotless.LineEnding.UNIX
		}
		kotlinGradle {
			target("**/*.gradle.kts")
			ktlint("1.7.1")
			lineEndings = com.diffplug.spotless.LineEnding.UNIX
		}
	}
}

// Configure Dokka v2 aggregation - only user-facing modules
dependencies {
	dokka(project(":mcp4k-runtime")) // Main API: @McpTool, @McpPrompt
	dokka(project(":mcp4k-gradle")) // Gradle plugin: id("sh.ondr.mcp4k")
	dokka(project(":mcp4k-file-provider")) // File provider utilities
	// Internal modules (mcp4k-ksp, mcp4k-compiler) are not included in public docs
}

dokka {
	dokkaPublications.html {
		outputDirectory.set(rootDir.resolve("build/dokka/html"))
	}
}

tasks.withType<DependencyUpdatesTask> {
	rejectVersionIf {
		isNonStable(candidate.version)
	}
}

fun isNonStable(version: String): Boolean {
	val upperVersion = version.uppercase()
	val unstableKeywords = listOf(
		"ALPHA",
		"BETA",
		"RC",
		"CR",
		"M",
		"PREVIEW",
		"SNAPSHOT",
		"DEV",
		"PRE",
		"BUILD",
		"NIGHTLY",
		"CANARY",
		"EAP",
		"MILESTONE",
	)

	return unstableKeywords.any { upperVersion.contains(it) }
}
