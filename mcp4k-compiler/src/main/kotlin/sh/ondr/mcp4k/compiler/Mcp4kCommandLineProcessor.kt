package sh.ondr.mcp4k.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

object Mcp4kConfigurationKeys {
	val ENABLED = CompilerConfigurationKey<Boolean>("enabled")
	val IS_TEST_SET = CompilerConfigurationKey<Boolean>("isTestSet")
}

@OptIn(ExperimentalCompilerApi::class)
class Mcp4kCommandLineProcessor : CommandLineProcessor {
	override val pluginId = "sh.ondr.mcp4k"

	override val pluginOptions = listOf(
		CliOption(
			optionName = "enabled",
			valueDescription = "true|false",
			description = "Whether mcp4k plugin is enabled",
			required = false,
			allowMultipleOccurrences = false,
		),
		CliOption(
			optionName = "isTestSet",
			valueDescription = "true|false",
			description = "Whether this is a test compilation",
			required = false,
			allowMultipleOccurrences = false,
		),
	)

	override fun processOption(
		option: AbstractCliOption,
		value: String,
		configuration: CompilerConfiguration,
	) {
		when (option.optionName) {
			"enabled" -> configuration.put(Mcp4kConfigurationKeys.ENABLED, value.toBoolean())
			"isTestSet" -> configuration.put(Mcp4kConfigurationKeys.IS_TEST_SET, value.toBoolean())
		}
	}
}
