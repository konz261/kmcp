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
	packageName("sh.ondr.kmcp.gradle")
	buildConfigField("String", "PLUGIN_VERSION", "\"$version\"")
}

gradlePlugin {
	plugins {
		create("main") {
			id = "sh.ondr.kmcp"
			implementationClass = "sh.ondr.kmcp.gradle.KmcpGradlePlugin"
		}
	}
}

// If the root project is NOT 'kmcp', we must be in `kmcp-build`
if (rootProject.name != "kmcp") {
	// Move build directory into `kmcp-build`
	layout.buildDirectory = file("$rootDir/build/kmcp-gradle-included")
}

// Only publish from real build
if (rootProject.name == "kmcp") {
	apply(plugin = "com.vanniktech.maven.publish")
}
