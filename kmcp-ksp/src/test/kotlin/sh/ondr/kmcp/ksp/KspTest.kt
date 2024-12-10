package sh.ondr.kmcp.ksp

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KspTest : BaseKspTest() {
	@Test
	fun testToolFunctionGeneration() {
		projectDir.resolve("src/commonMain/kotlin/test/Test.kt").apply {
			parentFile.mkdirs()
			writeText(
				"""
				package test

				import sh.ondr.kmcp.runtime.annotation.Tool
				import sh.ondr.kmcp.runtime.annotation.ToolArg

				@Tool(description = "This tool greets the user.")
				fun greet(
				    @ToolArg(name = "name", description = "The name of the user") name: String,
				    age: Int,
				    location: String = "San Francisco"
				): String {
				    return "Hello \${'$'}name, you are \${'$'}age years old and from \${'$'}location!"
				}
				""".trimIndent(),
			)
		}

		val initialResult =
			GradleRunner.create()
				.withProjectDir(projectDir)
				.withArguments("clean", "build")
				.withPluginClasspath()
				.forwardOutput()
				.build()

		assertTrue(initialResult.output.contains("BUILD SUCCESSFUL"), "Build was not successful.")

		val generatedFile =
			File(
				projectDir,
				"build/generated/ksp/jvm/jvmMain/kotlin/sh/ondr/kmcp/generated/GeneratedMcpRegistryInitializer.kt",
			)
		assertTrue(generatedFile.exists(), "Generated registry initializer file not found.")

		val actualContent = generatedFile.readText()

		val expectedContent = this::class.java.getResource("/expected/GeneratedMcpRegistryInitializer.kt.expected").readText()

		val expectedNormalized = normalizeWhitespace(expectedContent)
		val actualNormalized = normalizeWhitespace(actualContent)
		assertEquals(expectedNormalized, actualNormalized, "Generated code does not match expected output after formatting.")
	}
}

fun normalizeWhitespace(input: String) = input.lines().joinToString("\n") { it.trim() }
