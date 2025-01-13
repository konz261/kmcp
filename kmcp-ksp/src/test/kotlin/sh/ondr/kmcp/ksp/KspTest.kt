package sh.ondr.kmcp.ksp

import org.gradle.testkit.runner.GradleRunner
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

class KspTest : BaseKspTest() {
	// TODO broke, fix
	@Ignore
	@Test
	fun testToolFunctionGeneration() {
		projectDir.resolve("src/commonMain/kotlin/test/Test.kt").apply {
			parentFile.mkdirs()
			writeText(
				"""
				package test

				import sh.ondr.kmcp.runtime.annotation.McpTool
				import sh.ondr.kmcp.schema.content.ToolContent
				import kotlinx.serialization.Serializable
				
				@Serializable
				data class Location(
					val city: String,
					val country: String,
				)

				@McpTool
				fun greet(name: String, age: Int, location: Location): ToolContent {
				    return TextContent("Hello \${'$'}name, you are \${'$'}age years old and from \${'$'}{location.city} in \${'$'}{location.country}!")
				}
				""".trimIndent(),
			)

			val initialResult =
				GradleRunner.create()
					.withProjectDir(projectDir)
					.withArguments("clean", "build")
					.forwardOutput()
					.build()

			println(projectDir)
			assertTrue(initialResult.output.contains("BUILD SUCCESSFUL"), "Build was not successful.")
			checkGeneratedFile(
				projectDir = projectDir,
				className = "KmcpGeneratedToolRegistryInitializer",
			)
		}
	}
}
