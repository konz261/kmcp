package sh.ondr.kmcp.ksp

import org.gradle.testkit.runner.GradleRunner
import kotlin.test.Test
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

				@Tool(description = "This tool greets the user")
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
		checkGeneratedFile(projectDir, "KmcpGeneratedToolRegistryInitializer")
		checkGeneratedFile(projectDir, "KmcpGeneratedGreetParameters")
	}
}
