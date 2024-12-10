package sh.ondr.kmcp.ksp

import org.gradle.testkit.runner.GradleRunner
import kotlin.test.Test
import kotlin.test.assertTrue

class KspTest : BaseKspTest() {
	@Test
	fun testPluginIntegration() {
		projectDir.resolve("src/commonMain/kotlin/test/Test.kt").apply {
			parentFile.mkdirs()
			writeText(
				"""
				package test
				fun sayHello() = println("Hello from commonMain")
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
	}
}
