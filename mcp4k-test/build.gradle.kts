import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.ondrsh.mcp4k) // Will not use GAV coordinates, will be substituted
	alias(libs.plugins.maven.publish)
}

kotlin {
	jvm {
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_11)
		}
	}
	js(IR) {
		nodejs()
		binaries.library()
	}
	iosArm64()
	iosX64()
	iosSimulatorArm64()
	linuxX64()
	macosArm64()
	macosX64()

	sourceSets {
		commonMain {
			dependencies {
				implementation(libs.kotlinx.serialization.json)
				implementation(libs.kotlinx.coroutines.core)
			}
		}
		commonTest {
			dependencies {
				implementation(kotlin("test"))
				implementation(libs.kotlinx.coroutines.test)
				implementation(libs.square.okio)
				implementation(libs.square.okio.fakefilesystem)
			}
		}
	}
}

tasks.withType<Test>().configureEach {
	useJUnitPlatform()
	
	testLogging {
		events("passed", "skipped", "failed")
		showExceptions = true
		showCauses = true
		showStackTraces = true
	}
}

// Disable publishing for this test module
tasks.withType<PublishToMavenRepository>().configureEach {
	enabled = false
}
tasks.withType<PublishToMavenLocal>().configureEach {
	enabled = false
}

// Task to verify the plugin was applied correctly
tasks.register("verifyPluginApplication") {
	doLast {
		println("Kotlin version: ${libs.versions.kotlin.get()}")
		println("KSP tasks: ${tasks.names.filter { it.contains("ksp", ignoreCase = true) }}")
		println("MCP4K plugin applied: ${project.plugins.hasPlugin("sh.ondr.mcp4k")}")
	}
}
