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
import sh.ondr.kmcp.runtime.meta.ParameterMetadata
import sh.ondr.kmcp.runtime.meta.ToolMetadata

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

		// Generate @Serializable parameter classes for each tool
		toolMetadataList.forEach { tool ->
			generateParameterClass(tool)
		}

		// Generate the registry initializer
		generateKmcpToolRegistryInitializer(toolMetadataList)

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

					val paramDescription = toolArgAnno?.getStringArgument("description")?.ifEmpty { null }
					val paramTypeRef = param.type.resolve()
					val paramTypeName = paramTypeRef.declaration.qualifiedName?.asString() ?: paramTypeRef.toString()
					val isOptional = param.hasDefault

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

	private fun generateParameterClass(tool: ToolMetadata) {
		if (tool.parameters.isEmpty()) {
			return
		}

		val packageName = "$pkg.generated"
		val className = "KmcpGenerated${tool.name.replaceFirstChar { it.uppercase() }}Parameters"

		val fileContent =
			buildString {
				appendLine("package $packageName")
				appendLine()
				appendLine("import kotlinx.serialization.Serializable")

				appendLine("@Serializable")
				append("data class $className(")
				appendLine()
				tool.parameters.forEachIndexed { i, param ->
					val paramType = if (param.isOptional) "${param.type}?" else param.type
					append("    val ${param.name}: $paramType")
					if (param.isOptional) append(" = null") // optional param default
					if (i < tool.parameters.size - 1) append(",\n") else append("\n")
				}
				appendLine(")")
			}

		val file =
			codeGenerator.createNewFile(
				Dependencies(aggregating = false, *originatingFiles.toTypedArray()),
				packageName,
				"KmcpGenerated${tool.name.replaceFirstChar { it.uppercase() }}Parameters",
			)
		file.write(fileContent.toByteArray())
		file.close()
	}

	private fun generateKmcpToolRegistryInitializer(toolMetadataList: List<ToolMetadata>) {
		val packageName = "$pkg.generated"
		val fileName = "KmcpGeneratedToolRegistryInitializer"

		val fileContent =
			buildString {
				appendLine("package $packageName")
				appendLine()
				appendLine("import $pkg.runtime.KMCP")
				appendLine("import $pkg.runtime.tools.Tool")
				appendLine("import $pkg.runtime.tools.ToolHandler")
				appendLine("import $pkg.runtime.tools.ToolInputSchema")
				appendLine("import kotlinx.serialization.json.*")
				appendLine()
				appendLine("object $fileName {")
				appendLine("    init {")

				// For each tool, we initialize an empty map for defaults:
				toolMetadataList.forEach { tool ->
					appendLine("        KMCP.ToolRegistry.defaultValues[\"${tool.name}\"] = emptyMap()")
				}

				toolMetadataList.forEach { tool ->
					appendLine(generateToolRegistration(tool))
					appendLine(generateToolHandlerRegistration(tool))
				}

				appendLine("    }")
				appendLine("}")
			}

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
				val jsonType = typeToJsonType(param.type)
				buildString {
					append("                    \"${param.name}\" to buildJsonObject {\n")
					append("                        put(\"type\", JsonPrimitive($jsonType))\n")
					param.description?.let { desc ->
						append("                        put(\"description\", JsonPrimitive(\"$desc\"))\n")
					}
					append("                    }")
				}
			}

		val requiredParams = tool.parameters.filter { !it.isOptional }.joinToString(", ") { "\"${it.name}\"" }
		val requiredList = if (requiredParams.isEmpty()) "null" else "listOf($requiredParams)"

		return buildString {
			appendLine("        KMCP.ToolRegistry.tools[\"${tool.name}\"] = Tool(")
			appendLine("            name = \"${tool.name}\",")
			appendLine("            description = ${tool.description?.let { "\"$it\"" } ?: "null"},")
			appendLine("            inputSchema = ToolInputSchema(")
			appendLine("                type = \"object\",")
			if (tool.parameters.isNotEmpty()) {
				appendLine("                properties = mapOf(")
				append(parametersCode)
				appendLine("),")
			} else {
				// No parameters
				appendLine("                properties = null,")
			}
			appendLine("                required = $requiredList")
			appendLine("            )")
			appendLine("        )")
		}
	}

	private fun generateToolHandlerRegistration(tool: ToolMetadata): String {
		val paramsClassName = "KmcpGenerated${tool.name.replaceFirstChar { it.uppercase() }}Parameters"
		val indent = "                    " // Indentation for parameters

		// If no parameters, we don't decode anything and just call the function directly.
		if (tool.parameters.isEmpty()) {
			return buildString {
				appendLine("        KMCP.ToolRegistry.handlers[\"${tool.name}\"] = object : ToolHandler {")
				appendLine("            override fun invoke(params: JsonElement?): Any? {")
				// No decode needed, just call the function directly
				appendLine("                return ${tool.fqName}()")
				appendLine("            }")
				appendLine("        }")
			}
		}

		// If we have parameters, we decode them from JSON into our generated class
		val argList =
			tool.parameters.joinToString(",\n$indent") { param ->
				val paramAccess = "parameters.${param.name}"
				val castType = param.type
				if (param.isOptional) {
					"$paramAccess ?: (KMCP.ToolRegistry.defaultValues[\"${tool.name}\"]?.get(\"${param.name}\") as? $castType) " +
						"?: error(\"Parameter \\\"${param.name}\\\" of tool \\\"${tool.name}\\\" was not provided and no default is available.\")"
				} else {
					paramAccess
				}
			}

		return buildString {
			appendLine("        KMCP.ToolRegistry.handlers[\"${tool.name}\"] = object : ToolHandler {")
			appendLine("            override fun invoke(params: JsonElement?): Any? {")
			appendLine("                val parameters = KMCP.json.decodeFromJsonElement<$paramsClassName>(params ?: JsonNull)")
			appendLine("                return ${tool.fqName}(")
			appendLine("$indent$argList,")
			appendLine("                )")
			appendLine("            }")
			appendLine("        }")
		}
	}

	private fun typeToJsonType(typeName: String): String {
		return when (typeName) {
			"kotlin.Int", "kotlin.Long", "kotlin.Double" -> "\"number\""
			"kotlin.String" -> "\"string\""
			else -> "\"object\""
		}
	}
}
