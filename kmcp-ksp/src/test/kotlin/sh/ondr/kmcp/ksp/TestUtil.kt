package sh.ondr.kmcp.ksp

import org.gradle.internal.impldep.com.google.api.client.googleapis.testing.TestUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import kotlin.jvm.java

fun normalizeWhitespace(input: String) = input.lines().joinToString("\n") { it.trim() }

/**
 * Checks if a generated file exists and matches the expected content.
 *
 * @param projectDir The project directory used for the test.
 * @param className The class name of the generated file without the .kt extension.
 *                  For example "KmcpGeneratedToolRegistryInitializer" or "KmcpGeneratedGreetParameters".
 * @param packagePath The path to the generated package directory relative to "build/generated/ksp/jvm/jvmMain/kotlin".
 *                    For example "sh/ondr/kmcp/generated".
 */
fun checkGeneratedFile(
	projectDir: File,
	className: String,
	packagePath: String = "sh/ondr/kmcp/generated",
) {
	val generatedFile =
		File(
			projectDir,
			"build/generated/ksp/jvm/jvmMain/kotlin/$packagePath/$className.kt",
		)

	assertTrue(generatedFile.exists(), "Generated file not found for class: $className")

	val actualContent = generatedFile.readText()
	val expectedContent = TestUtils::class.java.getResource("/expected/$className.kt.expected").readText()

	val expectedNormalized = normalizeWhitespace(expectedContent)
	val actualNormalized = normalizeWhitespace(actualContent)
	assertEquals(expectedNormalized, actualNormalized, "Generated code for $className does not match expected output after formatting.")
}
