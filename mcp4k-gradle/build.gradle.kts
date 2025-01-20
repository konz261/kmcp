plugins {
	id("java-gradle-plugin")
	alias(libs.plugins.build.config)
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.maven.publish)
}

dependencies {
	compileOnly(libs.auto.service)
	compileOnly(libs.auto.service.annotations)
	compileOnly(libs.kotlin.compiler.embeddable)
	implementation(libs.koja.gradle)
	implementation(libs.kotlin.stdlib)
	implementation(libs.kotlin.gradle.api)
	implementation(libs.kotlin.gradle.plugin)
	implementation(libs.ksp.gradle.plugin)
}

buildConfig {
	useKotlinOutput {
		internalVisibility = true
		topLevelConstants = true
	}
	packageName("sh.ondr.mcp4k.gradle")
	buildConfigField("String", "PLUGIN_VERSION", "\"$version\"")
}

gradlePlugin {
	plugins {
		create("main") {
			id = "sh.ondr.mcp4k"
			implementationClass = "sh.ondr.mcp4k.gradle.Mcp4kGradlePlugin"
		}
	}
}

// If the root project is NOT 'mcp4k', we must be in `mcp4k-build`
if (rootProject.name != "mcp4k") {
	// Move build directory into `mcp4k-build`
	layout.buildDirectory = file("$rootDir/build/mcp4k-gradle-included")
}

// Only publish from real build
if (rootProject.name == "mcp4k") {
	apply(plugin = "com.vanniktech.maven.publish")
}
