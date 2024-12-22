import com.vanniktech.maven.publish.SonatypeHost

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
	mavenPublishing {
		publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
		signAllPublications()

		coordinates(
			groupId = project.group.toString(),
			artifactId = "kmcp-gradle",
			version = project.version.toString(),
		)

		pom {
			name = "KMCP Gradle Plugin"
			description = "KMCP: Kotlin Multiplatform MCP Framework Gradle Plugin"
			inceptionYear = "2024"
			url = "https://github.com/ondrsh/kmcp"
			licenses {
				license {
					name = "Apache License 2.0"
					url = "https://www.apache.org/licenses/LICENSE-2.0"
					distribution = "repo"
				}
			}
			developers {
				developer {
					id = "ondrsh"
					name = "Andreas Toth"
					url = "https://github.com/ondrsh"
				}
			}
			scm {
				url = "https://github.com/ondrsh/kmcp"
				connection = "scm:git:git://github.com/ondrsh/kmcp.git"
				developerConnection = "scm:git:ssh://git@github.com/ondrsh/kmcp.git"
			}
		}
	}
}
