dependencyResolutionManagement {
	versionCatalogs {
		create("libs") {
			from(files("../gradle/libs.versions.toml"))
		}
	}
}

rootProject.name = "mcp4k-build"

// Re-expose `mcp4k-gradle` project so we can substitute the GAV coords with it when building internally
include(":gradle-plugin")
project(":gradle-plugin").projectDir = file("../mcp4k-gradle")
