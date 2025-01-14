package sh.ondr.kmcp.ksp.tools

import com.google.devtools.ksp.processing.Dependencies
import sh.ondr.kmcp.ksp.KmcpProcessor
import sh.ondr.kmcp.ksp.toFqnString

fun KmcpProcessor.generateToolParamsClass(toolMeta: ToolMeta) {
	val code = buildString {
		appendLine("package $kmcpParamsPackage")
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
		appendLine("data class ${toolMeta.paramsClassName}(")

		toolMeta.params.forEachIndexed { index, p ->
			val comma = if (index == toolMeta.params.size - 1) "" else ","

			// 1) Get the base (non-nullable) FQN from KSType
			val baseFqn = p.ksType.makeNotNullable().toFqnString()

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

	val dependencies = Dependencies(false, toolMeta.originatingFile)
	codeGenerator.createNewFile(
		dependencies = dependencies,
		packageName = kmcpParamsPackage,
		fileName = toolMeta.paramsClassName,
	).use { output ->
		output.writer().use { writer ->
			writer.write(code)
		}
	}
}
