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
				import kotlinx.serialization.Serializable
				
				@Serializable
				data class User(
					val name: String,
					val age: Int,
				)

				@Tool(description = "This tool greets the user")
				fun User.greet(): Unit {
				    println("Hello \${'$'}name, you are \${'$'}age years old and from \${'$'}location!")
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
