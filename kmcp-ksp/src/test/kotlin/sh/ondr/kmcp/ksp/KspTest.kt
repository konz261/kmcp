package sh.ondr.kmcp.ksp

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class KspTest {
	@Test
	fun testPluginIntegration() {
		val projectDir =
			Files.createTempDirectory("testProject").toFile().apply {
				deleteOnExit()
			}
		projectDir.apply {
			// settings.gradle.kts
			resolve("settings.gradle.kts").writeText(
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

			// build.gradle.kts
			resolve("build.gradle.kts").writeText(
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
						val commonMain by getting {
							dependencies {
						  }
						}
						
						val commonTest by getting {
							dependencies {
						  	implementation(kotlin("test"))
						  }
						}
					}
				}
				""".trimIndent(),
			)

			val commonMainDir = resolve("src/commonMain/kotlin/test")
			commonMainDir.mkdirs()
			File(commonMainDir, "Test.kt").writeText(
				"""
				package test

				// This should be available in common code once stdlib-common is declared.
				fun sayHello() {
				    println("Hello from commonMain")
				}
				""".trimIndent(),
			)
		}

		val result =
			GradleRunner.create()
				.withProjectDir(projectDir)
				.withArguments("clean", "build")
				.withPluginClasspath()
				.forwardOutput()
				.build()

		assertTrue(result.output.contains("BUILD SUCCESSFUL"))

		val classesDir = File(projectDir, "build/classes/kotlin/jvm/main")
		assertTrue(classesDir.exists())
	}
}
