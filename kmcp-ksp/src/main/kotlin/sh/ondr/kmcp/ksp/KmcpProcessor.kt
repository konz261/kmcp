package sh.ondr.kmcp.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import sh.ondr.kmcp.ksp.TypeUtil.addTypeHint
import sh.ondr.kmcp.runtime.meta.ParameterMetadata
import sh.ondr.kmcp.runtime.meta.ToolMetadata
import kotlin.collections.isNotEmpty
import kotlin.collections.map
import kotlin.collections.mapNotNull
import kotlin.collections.toTypedArray

class KmcpProcessor(
	private val codeGenerator: CodeGenerator,
	private val logger: KSPLogger,
	private val options: Map<String, String>,
) : SymbolProcessor {
	private val pkg = "sh.ondr.kmcp"
	private val toolAnnoFqn = "$pkg.runtime.annotation.Tool"
	private val toolArgAnnoFqn = "$pkg.runtime.annotation.ToolArg"

	fun KSAnnotation.isToolArgAnno() = annotationType.resolve().declaration.qualifiedName?.asString() == toolArgAnnoFqn

	fun KSAnnotation.isToolAnno() = annotationType.resolve().declaration.qualifiedName?.asString() == toolAnnoFqn

	fun KSAnnotation.getStringArgument(name: String): String? = arguments.find { it.name?.asString() == name }?.value as? String

	private var originatingFiles: List<KSFile> = emptyList()

	override fun process(resolver: Resolver): List<KSAnnotated> {
		val toolFunctions =
			resolver.getSymbolsWithAnnotation(toolAnnoFqn)
				.filterIsInstance<KSFunctionDeclaration>()
				.toList()

		val toolMetadataList = collectToolMetadata(toolFunctions)

		// Check for duplicate tool names
		if (toolMetadataList.isNotEmpty()) {
			val duplicates = toolMetadataList.map { it to it.name }.groupBy { it.second }.filter { it.value.size > 1 }
			if (duplicates.isNotEmpty()) {
				logger.error("Multiple tools with the same name found.")
				val namesToTools = duplicates.mapValues { entry -> entry.value.map { it.first.fqName } }.entries
				namesToTools.forEach { (name, tools) ->
					val errorMsg = "Multiple tools with the same name found. Name: \"$name\", tools: $tools"
					logger.error(errorMsg)
				}
				return emptyList()
			}
		}

		generateMcpRegistryInitializer(toolMetadataList)

		return emptyList()
	}

	private fun collectToolMetadata(toolFunctions: List<KSFunctionDeclaration>): List<ToolMetadata> {
		if (toolFunctions.isEmpty()) return emptyList()
		originatingFiles = toolFunctions.mapNotNull { it.containingFile }

		return toolFunctions.map { function ->
			val toolAnno = function.annotations.find { it.isToolAnno() }
			val toolDescription = toolAnno?.getStringArgument("description")?.ifEmpty { null }
			val toolName = function.simpleName.asString()

			val parameters =
				function.parameters.map { param ->
					val toolArgAnno: KSAnnotation? = param.annotations.find { it.isToolArgAnno() }
					val paramName =
						toolArgAnno?.getStringArgument("name")?.ifEmpty { null }
							?: param.name?.asString() ?: error("Could not get parameter name")
					var paramDescription = toolArgAnno?.getStringArgument("description")?.ifEmpty { null }
					val paramTypeRef = param.type.resolve()
					val paramTypeName = paramTypeRef.declaration.qualifiedName?.asString() ?: paramTypeRef.toString()
					val isOptional = param.hasDefault

					// Add type hints if needed
					paramDescription =
						when (paramTypeName) {
							"kotlin.Int" -> paramDescription?.addTypeHint(TypeUtil.INT_INFO)
							"kotlin.Long" -> paramDescription?.addTypeHint(TypeUtil.LONG_INFO)
							"kotlin.Double", "kotlin.String" -> paramDescription
							else -> error("Unsupported parameter type: $paramTypeName")
						}

					ParameterMetadata(
						name = paramName,
						type = paramTypeName,
						description = paramDescription,
						isOptional = isOptional,
					)
				}

			val returnTypeRef = function.returnType?.resolve()
			val returnTypeName = returnTypeRef?.declaration?.qualifiedName?.asString() ?: returnTypeRef.toString()

			ToolMetadata(
				fqName = function.qualifiedName?.asString() ?: function.simpleName.asString(),
				name = toolName,
				description = toolDescription,
				parameters = parameters,
				returnType = returnTypeName,
			)
		}
	}

	private fun generateMcpRegistryInitializer(toolMetadataList: List<ToolMetadata>) {
		val packageName = "$pkg.generated"
		val fileName = "GeneratedMcpRegistryInitializer"

		val fileContent =
			buildString {
				appendLine("package $packageName")
				appendLine()
				appendLine("import $pkg.runtime.McpRegistry")
				appendLine("import $pkg.runtime.meta.ParameterMetadata")
				appendLine("import $pkg.runtime.meta.ToolMetadata")
				appendLine("import $pkg.runtime.tools.Tool")
				appendLine("import $pkg.runtime.tools.ToolHandler")
				appendLine("import $pkg.runtime.tools.ToolInputSchema")
				appendLine("import kotlinx.serialization.json.JsonPrimitive")
				appendLine("import kotlinx.serialization.json.buildJsonObject")
				appendLine()

				appendLine("object $fileName {")
				appendLine("    init {")

				toolMetadataList.forEach { tool ->
					appendLine(generateToolRegistration(tool))
					appendLine(generateToolHandlerRegistration(tool))
				}

				appendLine("    }")
				appendLine("}")
			}

		logger.info("Generated file: $packageName.$fileName, dependencies: $originatingFiles")
		try {
			val file =
				codeGenerator.createNewFile(
					Dependencies(aggregating = false, *originatingFiles.toTypedArray()),
					packageName,
					fileName,
				)
			file.write(fileContent.toByteArray())
			file.close()
		} catch (e: FileAlreadyExistsException) {
			// KSP running multiple times... ignore? TODO - maybe check run count
		}
	}

	private fun generateToolRegistration(tool: ToolMetadata): String {
		val parametersCode =
			tool.parameters.joinToString(",\n") { param ->
				buildString {
					append("                    \"${param.name}\" to buildJsonObject {\n")
					append("                        put(\"type\", JsonPrimitive(${typeToJsonType(param.type)}))\n")
					val paramDesc = param.description?.let { "\"$it\"" } ?: "null"
					if (paramDesc != "null") {
						append("                        put(\"description\", JsonPrimitive($paramDesc))\n")
					}
					append("                    }")
				}
			}

		val requiredParams = tool.parameters.filter { !it.isOptional }.joinToString(", ") { "\"${it.name}\"" }
		val requiredList = if (requiredParams.isEmpty()) "null" else "listOf($requiredParams)"

		return buildString {
			appendLine("        McpRegistry.globalTools[\"${tool.name}\"] = Tool(")
			appendLine("            name = \"${tool.name}\",")
			appendLine("            description = ${tool.description?.let { "\"$it\"" } ?: "null"},")
			appendLine("            inputSchema = ToolInputSchema(")
			appendLine("                type = \"object\",")
			appendLine("                properties = mapOf(")
			append(parametersCode)
			appendLine("),")
			appendLine("                required = $requiredList")
			appendLine("            )")
			appendLine("        )")
		}
	}

	private fun generateToolHandlerRegistration(tool: ToolMetadata): String {
		val requiredParams = tool.parameters.filter { !it.isOptional }
		val optionalParams = tool.parameters.filter { it.isOptional }

		val paramHandlingCode = StringBuilder()

		// Required params
		for (param in requiredParams) {
			val paramName = param.name
			paramHandlingCode.appendLine("            val ${paramName}Arg = arguments[\"$paramName\"]")
			paramHandlingCode.appendLine("            if (${paramName}Arg == null) error(\"Missing parameter $paramName\")")
			val decodeExpression = decodeParamExpression(paramName, param.type)
			paramHandlingCode.appendLine("            val typed${paramName.firstLetterUppercase()} = $decodeExpression")
		}

		// Optional params
		for (param in optionalParams) {
			val paramName = param.name
			paramHandlingCode.appendLine("            val ${paramName}Arg = arguments[\"$paramName\"]")
			val decodeExpression = decodeParamExpression(paramName, param.type)
			paramHandlingCode.appendLine(
				"            val typed${paramName.firstLetterUppercase()} = if (${paramName}Arg != null) $decodeExpression else null",
			)
		}

		if (optionalParams.isEmpty()) {
			val argList = requiredParams.joinToString(", ") { "${it.name} = typed${it.name.firstLetterUppercase()}" }
			paramHandlingCode.appendLine("            return ${tool.fqName}($argList)")
		} else {
			val firstOptional = optionalParams.first()
			val requiredArgList = requiredParams.joinToString(", ") { "${it.name} = typed${it.name.firstLetterUppercase()}" }

			val withOptionalArgList =
				if (requiredParams.isEmpty()) {
					"${firstOptional.name} = typed${firstOptional.name.firstLetterUppercase()}"
				} else {
					"$requiredArgList, ${firstOptional.name} = typed${firstOptional.name.firstLetterUppercase()}"
				}

			val withoutOptionalArgList =
				if (requiredParams.isEmpty()) {
					""
				} else {
					requiredArgList
				}

			paramHandlingCode.appendLine("            if (typed${firstOptional.name.firstLetterUppercase()} != null) {")
			paramHandlingCode.appendLine("                return ${tool.fqName}($withOptionalArgList)")
			paramHandlingCode.appendLine("            } else {")
			paramHandlingCode.appendLine("                return ${tool.fqName}($withoutOptionalArgList)")
			paramHandlingCode.appendLine("            }")
		}

		return buildString {
			appendLine("        McpRegistry.globalToolHandlers[\"${tool.name}\"] = object : ToolHandler {")
			appendLine("            override fun invoke(arguments: Map<String, Any?>): Any? {")
			append(paramHandlingCode)
			appendLine("            }")
			appendLine("        }")
		}
	}

	private fun decodeParamExpression(
		paramName: String,
		typeName: String,
	): String {
		return when (typeName) {
			"kotlin.Int" -> "((${paramName}Arg as? Number)?.toInt() ?: error(\"$paramName must be a 32-Bit Integer\"))"
			"kotlin.Long" -> "((${paramName}Arg as? Number)?.toLong() ?: error(\"$paramName must be a 64-Bit Integer\"))"
			"kotlin.Double" -> "((${paramName}Arg as? Number)?.toDouble() ?: error(\"$paramName must be Double\"))"
			"kotlin.String" -> "(${paramName}Arg as? String ?: error(\"$paramName must be String\"))"
			else -> "error(\"Unsupported parameter type $typeName\")"
		}
	}

	private fun typeToJsonType(typeName: String): String {
		return when (typeName) {
			"kotlin.Int", "kotlin.Long", "kotlin.Double" -> "\"number\""
			"kotlin.String" -> "\"string\""
			else -> error("Unsupported parameter type $typeName")
		}
	}

	private fun String.firstLetterUppercase() = replaceFirstChar { it.uppercase() }
}
