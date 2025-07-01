package sh.ondr.mcp4k.ksp.tools

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSFile
import sh.ondr.mcp4k.ksp.Mcp4kProcessor

fun Mcp4kProcessor.generateToolParamsClass(
	toolMeta: ToolMeta,
	originFile: KSFile,
) {
	val code = buildString {
		appendLine("package $mcp4kParamsPackage")
		appendLine()
		appendLine("import kotlinx.serialization.json.decodeFromJsonElement")
		appendLine("import kotlinx.serialization.json.JsonElement")
		appendLine("import kotlinx.serialization.Serializable")
		appendLine("import sh.ondr.koja.JsonSchema")
		appendLine()

		toolMeta.kdoc?.let { kdoc ->
			appendLine("/**")
			kdoc.split("\n").forEach { line ->
				appendLine(" * $line")
			}
			appendLine("*/")
		}

		appendLine("@Serializable")
		appendLine("@JsonSchema")
		appendLine("class ${toolMeta.paramsClassName}(")

		toolMeta.params.forEachIndexed { index, p ->
			val comma = if (index == toolMeta.params.size - 1) "" else ","

			// 1) Get the base (non-nullable) FQN
			val baseFqn = p.fqnTypeNonNullable

			// 2) Append '?' if it’s either nullable or hasDefault
			val finalType = if (!p.hasDefault && !p.isNullable) baseFqn else "$baseFqn?"

			if (!p.hasDefault && !p.isNullable) {
				// Required param => no default
				append("    val ${p.name}: $finalType$comma\n")
			} else {
				// Optional param => default to null so it can be omitted at JSON deserialization
				append("    val ${p.name}: $finalType = null$comma\n")
			}
		}
		appendLine(")")
		appendLine()
		appendLine("/**")
		appendLine(" * ${toolMeta.fqName}")
		appendLine("*/")
		appendLine("const val ${toolMeta.paramsClassName}OriginalSource = true")
	}

	// Use isolating dependencies with the originating source file
	val dependencies = Dependencies(aggregating = false, sources = arrayOf(originFile))
	
	try {
		codeGenerator
			.createNewFile(
				dependencies = dependencies,
				packageName = mcp4kParamsPackage,
				fileName = toolMeta.paramsClassName,
				extensionName = "kt",
			).use { output ->
				output.writer().use { writer ->
					writer.write(code)
				}
			}
	} catch (e: Exception) {
		// File already exists from a previous round, skip
		if (e.message?.contains("already exists") == true) {
			logger.info("Skipping already generated file: ${toolMeta.paramsClassName}.kt")
		} else {
			throw e
		}
	}
}
