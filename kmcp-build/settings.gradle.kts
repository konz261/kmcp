dependencyResolutionManagement {
	versionCatalogs {
		create("libs") {
			from(files("../gradle/libs.versions.toml"))
		}
	}
}

rootProject.name = "kmcp-build"

// Re-expose `kmcp-gradle` project so we can substitute the GAV coords with it when building internally
include(":gradle-plugin")
project(":gradle-plugin").projectDir = file("../kmcp-gradle")
