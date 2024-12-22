import com.vanniktech.maven.publish.SonatypeHost

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.maven.publish)
	alias(libs.plugins.ondrsh.kmcp) // Will not use GAV coordinates, will be substituted
}

kotlin {

	iosArm64()
	iosX64()
	iosSimulatorArm64()
	js(IR) { nodejs() }
	jvm()
	linuxX64()
	macosArm64()

	sourceSets {
		commonMain {
			dependencies {
				implementation(libs.ondrsh.jsonschema)
				implementation(libs.kotlinx.atomicfu)
				implementation(libs.kotlinx.coroutines.core)
				api(libs.kotlinx.serialization.core)
				api(libs.kotlinx.serialization.json)
			}
		}
		commonTest {
			dependencies {
				implementation(libs.kotlinx.coroutines.test)
				implementation(kotlin("test"))
			}
		}
	}
}

mavenPublishing {
	publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
	signAllPublications()

	coordinates(
		groupId = project.group.toString(),
		artifactId = "kmcp-runtime",
		version = project.version.toString(),
	)

	pom {
		name = "KMCP Runtime"
		description = "KMCP: Kotlin Multiplatform MCP Framework Runtime"
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
