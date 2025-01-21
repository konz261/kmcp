package sh.ondr.mcp4k.ksp.prompts

import com.google.devtools.ksp.processing.Dependencies
import sh.ondr.mcp4k.ksp.Mcp4kProcessor
import sh.ondr.mcp4k.ksp.toFqnString

fun Mcp4kProcessor.generatePromptParamsClass(promptMeta: PromptMeta) {
	val code = buildString {
		appendLine("package $mcp4kParamsPackage")
		appendLine()
		appendLine("import kotlinx.serialization.Serializable")
		appendLine("import sh.ondr.koja.JsonSchema")
		appendLine()

		promptMeta.kdoc?.let { kdoc ->
			appendLine("/**")
			kdoc.split("\n").forEach { line ->
				appendLine(" * $line")
			}
			appendLine("*/")
		}

		appendLine("@Serializable")
		appendLine("@JsonSchema")
		appendLine("class ${promptMeta.paramsClassName}(")

		promptMeta.params.forEachIndexed { index, p ->
			val comma = if (index == promptMeta.params.size - 1) "" else ","

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
		appendLine(" * ${promptMeta.fqName}")
		appendLine("*/")
		appendLine("const val ${promptMeta.paramsClassName}OriginalSource = true")
	}

	val dependencies = Dependencies(false, promptMeta.originatingFile)
	codeGenerator.createNewFile(
		dependencies = dependencies,
		packageName = mcp4kParamsPackage,
		fileName = promptMeta.paramsClassName,
	).use { output ->
		output.writer().use { writer ->
			writer.write(code)
		}
	}
}
