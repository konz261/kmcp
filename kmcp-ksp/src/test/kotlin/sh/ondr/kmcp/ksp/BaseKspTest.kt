package sh.ondr.kmcp.ksp

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.io.File
import java.nio.file.Files

// TODO fix
abstract class BaseKspTest {
	lateinit var projectDir: File

	@BeforeEach
	fun setUp() {
		projectDir = createMultiplatformProject()
	}

	@AfterEach
	fun tearDown() {
		projectDir.deleteRecursively()
	}

	fun createMultiplatformProject(
		projectName: String = "testProject",
		configure: (File) -> Unit = {},
	): File {
		val projectDir =
			Files.createTempDirectory(projectName).toFile().apply {
				deleteOnExit()
			}

		// Write common files
		projectDir.resolve("settings.gradle.kts").writeText(
			"""
			pluginManagement {
			    repositories {
			        gradlePluginPortal()
			        mavenCentral()
			        mavenLocal()
			    }
			}
			""".trimIndent(),
		)

		projectDir.resolve("build.gradle.kts").writeText(
			"""
			repositories {
			    mavenCentral()
			    mavenLocal()
			}
			
			plugins {
			    id("org.jetbrains.kotlin.multiplatform") version "2.1.0"
			    id("sh.ondr.kmcp") version "0.1.0"
			}

			kotlin {
			    jvm()
			    js(IR) {
			        nodejs()
			    }

			    macosArm64()
			    iosX64()
			    iosArm64()
					
					sourceSets {
						commonMain {
							dependencies {
								implementation("sh.ondr:kotlin-json-schema:0.1.0")
							}
						}
					}
					
					
			}
			""".trimIndent(),
		)

		configure(projectDir)
		return projectDir
	}
}
