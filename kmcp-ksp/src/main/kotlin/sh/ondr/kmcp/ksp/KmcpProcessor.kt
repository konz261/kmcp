package sh.ondr.kmcp.ksp

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Visibility

// TODO clean this up
class KmcpProcessor(
	private val codeGenerator: CodeGenerator,
	private val logger: KSPLogger,
	private val options: Map<String, String>,
) : SymbolProcessor {
	private val pkg = "sh.ondr.kmcp"
	private val toolAnnoFqn = "$pkg.runtime.annotation.Tool"
	private val generatedPkg = "$pkg.generated"

	private val collectedFunctions = mutableListOf<ToolHelper>()

	override fun process(resolver: Resolver): List<KSAnnotated> {
		val toolFunctions =
			resolver.getSymbolsWithAnnotation(toolAnnoFqn)
				.filterIsInstance<KSFunctionDeclaration>()
				.toList()

		if (toolFunctions.isEmpty()) {
			return emptyList()
		}

		val newHelpers = toolFunctions.mapNotNull { it.toToolHelperOrNull() }
		if (newHelpers.isNotEmpty()) {
			collectedFunctions.addAll(newHelpers)
		}

		return emptyList()
	}

	override fun finish() {
		if (collectedFunctions.isEmpty()) {
			return
		}

		// Parse docstrings first
		for (tool in collectedFunctions) {
			val docString = tool.ksFunction.docString
			if (docString != null) {
				val parameters = tool.params.map { it.name }
				try {
					val (mainDesc, paramDescriptions) = docString.parseDescription(parameters)
					tool.description = mainDesc
					for (p in tool.params) {
						p.description = paramDescriptions[p.name]
					}
				} catch (e: IllegalArgumentException) {
					logger.error(
						"KMCP error when parsing KDoc for @Tool function '${tool.functionName}': ${e.message}",
					)
				}
			}
		}

		val errorsFound = checkToolFunctions(collectedFunctions)

		if (errorsFound) {
			logger.error("KMCP Error: aborting code generation due to errors in @Tool-annotated functions.")
			return
		}

		generateParamsFile()
		generateHandlersFile()
		generateRegistryInitializer()
	}

	/**
	 * Validate all collected @Tool annotated functions and log errors if any.
	 * @return true if errors found, false otherwise.
	 */
	private fun checkToolFunctions(tools: List<ToolHelper>): Boolean {
		var errorsFound = false

		// 1. Unique tool names
		tools.groupBy { it.functionName }
			.filterValues { it.size > 1 }
			.forEach { (name, toolsList) ->
				val fqNames = toolsList.joinToString(", ") { it.fqName }
				logger.error(
					"KMCP error: Multiple @Tool functions share the name '$name'. " +
						"Tool names must be unique. Conflicts: $fqNames",
				)
				errorsFound = true
			}

		// 2. Return type must be ToolContent
		tools.filter { !it.returnTypeFqn.endsWith("ToolContent") }
			.forEach { tool ->
				logger.error(
					"KMCP error: @Tool function '${tool.functionName}' must return the ToolContent interface. " +
						"Currently returns: ${tool.returnTypeReadable}. " +
						"Please change the return type to ToolContent.",
				)
				errorsFound = true
			}

		// 3. Parameter types must be supported
		tools.forEach { tool ->
			tool.params
				.mapNotNull { param ->
					val typeError = checkTypeSupported(param.ksType)
					if (typeError != null) param to typeError else null
				}
				.forEach { (param, typeError) ->
					logger.error(
						"KMCP error: @Tool function '${tool.functionName}' has a parameter '${param.name}' " +
							"of unsupported type '${param.readableType}':\nReason: $typeError",
					)
					errorsFound = true
				}
		}

		// 4. Limit non-required parameters
		val nonRequiredLimit = 7
		tools.filter { it.params.count { p -> !p.isRequired } > nonRequiredLimit }
			.forEach { tool ->
				logger.error(
					"KMCP error: @Tool function '${tool.functionName}' has more than $nonRequiredLimit non-required parameters. " +
						"Please reduce optional/nullable/default parameters to $nonRequiredLimit or fewer.",
				)
				errorsFound = true
			}

		// 5. Must be top-level function
		tools.filter { it.ksFunction.parentDeclaration != null }
			.forEach { tool ->
				val parent = tool.ksFunction.parentDeclaration?.qualifiedName?.asString() ?: "unknown parent"
				logger.error(
					"KMCP error: @Tool function '${tool.functionName}' is defined inside a class or object ($parent). " +
						"@Tool functions must be top-level. Move '${tool.functionName}' to file scope.",
				)
				errorsFound = true
			}

		// 6. Must have no disallowed modifiers
		val disallowedModifiers =
			setOf(
				Modifier.INLINE,
				Modifier.PRIVATE,
				Modifier.PROTECTED,
				Modifier.INTERNAL,
				Modifier.ABSTRACT,
				Modifier.OPEN,
				Modifier.SUSPEND,
			)

		tools.forEach { tool ->
			val visibility = tool.ksFunction.getVisibility()
			if (visibility != Visibility.PUBLIC) {
				logger.error(
					"KMCP error: @Tool function '${tool.functionName}' must be public. " +
						"Current visibility: $visibility. " +
						"Please ensure it's a top-level public function with no modifiers.",
				)
				errorsFound = true
			}

			val foundDisallowed = tool.ksFunction.modifiers.filter { it in disallowedModifiers }
			if (foundDisallowed.isNotEmpty()) {
				logger.error(
					"KMCP error: @Tool function '${tool.functionName}' has disallowed modifiers: $foundDisallowed. " +
						"Only a public, top-level, non-suspend, non-inline, and non-abstract/protected/private/internal function is allowed.",
				)
				errorsFound = true
			}
		}

		return errorsFound
	}

	/**
	 * Checks if a given type is supported by our schema rules.
	 * @return null if supported, otherwise a detailed error message.
	 */
	private fun checkTypeSupported(type: KSType): String? {
		val primitiveTypes =
			setOf(
				"kotlin.String", "kotlin.Char", "kotlin.Boolean",
				"kotlin.Byte", "kotlin.Short", "kotlin.Int", "kotlin.Long",
				"kotlin.Float", "kotlin.Double",
			)

		val decl = type.declaration
		val qName = decl.qualifiedName?.asString()

		// Primitives
		if (qName != null && primitiveTypes.contains(qName)) {
			return null
		}

		// Lists
		if (qName == "kotlin.collections.List") {
			if (type.arguments.size != 1) {
				return "List must have exactly one type parameter."
			}
			val inner = type.arguments[0].type?.resolve() ?: return "Unable to resolve List element type."
			val innerError = checkTypeSupported(inner)
			return innerError?.let { "List element type not supported: $it" }
		}

		// Maps
		if (qName == "kotlin.collections.Map") {
			if (type.arguments.size != 2) {
				return "Map must have exactly two type parameters (key and value)."
			}
			val keyType = type.arguments[0].type?.resolve() ?: return "Unable to resolve Map key type."
			val valueType = type.arguments[1].type?.resolve() ?: return "Unable to resolve Map value type."

			val keyQName = keyType.declaration.qualifiedName?.asString()
			if (keyQName != "kotlin.String") {
				return "Map key must be String, found: ${keyQName ?: "unknown"}."
			}
			val valueError = checkTypeSupported(valueType)
			return valueError?.let { "Map value type not supported: $it" }
		}

		// Enums or @Serializable classes
		val classDecl = decl as? KSClassDeclaration
		if (classDecl != null) {
			if (classDecl.classKind == ClassKind.ENUM_CLASS) {
				return null
			}

			val serializableFqn = "kotlinx.serialization.Serializable"
			val isSerializable =
				classDecl.annotations.any {
					it.annotationType.resolve().declaration.qualifiedName?.asString() == serializableFqn
				}
			if (isSerializable) {
				return null
			}

			return "Type '${classDecl.qualifiedName?.asString()}' is not a supported primitive, collection, enum, or @Serializable class."
		}

		return "Type '${qName ?: type.toString()}' is not supported."
	}

	private fun generateParamsFile() {
		val fileName = "KmcpGeneratedToolParams"
		val allFiles = collectedFunctions.flatMap { it.originatingFiles }.distinct().toTypedArray()
		val file =
			codeGenerator.createNewFile(
				dependencies = Dependencies(aggregating = true, sources = allFiles),
				packageName = generatedPkg,
				fileName = fileName,
			)

		val code =
			buildString {
				appendLine("// Generated by KMCP")
				appendLine("package $generatedPkg")
				appendLine()
				appendLine("import kotlinx.serialization.Serializable")
				appendLine()

				for (helper in collectedFunctions) {
					val paramsClassName = "KmcpGenerated${helper.functionName.replaceFirstChar { it.uppercaseChar() }}Params"
					appendLine("@Serializable")
					append("data class $paramsClassName(\n")
					helper.params.forEachIndexed { index, p ->
						val comma = if (index == helper.params.size - 1) "" else ","
						if (!p.hasDefault && !p.isNullable) {
							append("    val ${p.name}: ${p.fqnType}$comma\n")
						} else {
							append("    val ${p.name}: ${p.fqnType}? = null$comma\n")
						}
					}
					appendLine(")")
					appendLine()
				}
			}

		file.write(code.toByteArray())
		file.close()
	}

	private fun generateHandlersFile() {
		val fileName = "KmcpGeneratedToolHandlers"
		val allFiles = collectedFunctions.flatMap { it.originatingFiles }.distinct().toTypedArray()
		val file =
			codeGenerator.createNewFile(
				dependencies = Dependencies(aggregating = true, sources = allFiles),
				packageName = generatedPkg,
				fileName = fileName,
			)

		val code =
			buildString {
				appendLine("// Generated by KMCP")
				appendLine("package $generatedPkg")
				appendLine()
				appendLine("import kotlinx.serialization.json.JsonObject")
				appendLine("import kotlinx.serialization.json.decodeFromJsonElement")
				appendLine("import sh.ondr.kmcp.runtime.kmcpJson")
				appendLine("import sh.ondr.kmcp.schema.tools.CallToolResult")
				appendLine("import sh.ondr.kmcp.schema.content.ToolContent")
				appendLine("import sh.ondr.kmcp.runtime.tools.ToolHandler")
				appendLine()

				for (helper in collectedFunctions) {
					val handlerClassName = "KmcpGenerated${helper.functionName.replaceFirstChar { it.uppercaseChar() }}Handler"
					val paramsClassName = "KmcpGenerated${helper.functionName.replaceFirstChar { it.uppercaseChar() }}Params"

					appendLine("class $handlerClassName : ToolHandler {")
					appendLine("    override fun call(params: JsonObject): CallToolResult {")
					appendLine("        val obj = kmcpJson.decodeFromJsonElement($paramsClassName.serializer(), params)")
					appendLine("        val result = ${generateInvocationCode(helper, 2)}")
					appendLine("        return CallToolResult(listOf(result))")
					appendLine("    }")
					appendLine("}")
					appendLine()
				}
			}

		file.write(code.toByteArray())
		file.close()
	}

	private fun generateRegistryInitializer() {
		val fileName = "KmcpGeneratedToolRegistryInitializer"
		val allFiles = collectedFunctions.flatMap { it.originatingFiles }.distinct().toTypedArray()
		val file =
			codeGenerator.createNewFile(
				dependencies = Dependencies(aggregating = true, sources = allFiles),
				packageName = generatedPkg,
				fileName = fileName,
			)

		val code =
			buildString {
				appendLine("// Generated by KMCP")
				appendLine("package $generatedPkg")
				appendLine()
				appendLine("import sh.ondr.kmcp.runtime.KMCP")
				appendLine("import sh.ondr.kmcp.schema.tools.ToolInfo")
				appendLine("import sh.ondr.jsonschema.jsonSchema")
				appendLine()
				appendLine("object KmcpGeneratedToolRegistryInitializer {")
				appendLine("    init {")
				for (helper in collectedFunctions) {
					val paramsClassName = "KmcpGenerated${helper.functionName.replaceFirstChar { it.uppercaseChar() }}Params"
					val handlerClassName = "KmcpGenerated${helper.functionName.replaceFirstChar { it.uppercaseChar() }}Handler"
					val toolName = helper.functionName

					appendLine("        // Register '$toolName'")
					appendLine("        val ${toolName}ToolInfo = ToolInfo(")
					appendLine("            name = \"$toolName\",")
					val toolDescription = if (helper.description != null) "\"${helper.description}\"" else "null"
					appendLine("            description = $toolDescription,")
					appendLine("            inputSchema = jsonSchema<$paramsClassName>()")
					appendLine("        )")
					appendLine("        KMCP.toolInfos[\"$toolName\"] = ${toolName}ToolInfo")
					appendLine("        KMCP.toolHandlers[\"$toolName\"] = $handlerClassName()")
					appendLine()
				}
				appendLine("    }")
				appendLine("}")
			}

		file.write(code.toByteArray())
		file.close()
	}

	private fun generateInvocationCode(
		helper: ToolHelper,
		level: Int,
	): String {
		val defaultParams = helper.params.filter { it.hasDefault }
		val requiredParams = helper.params.filter { !it.hasDefault }
		return generateOptionalChain(helper, requiredParams, defaultParams, level)
	}

	private fun generateOptionalChain(
		helper: ToolHelper,
		requiredParams: List<ParamInfo>,
		defaultParams: List<ParamInfo>,
		level: Int,
	): String {
		if (defaultParams.isEmpty()) {
			return callFunction(helper.fqName, requiredParams, emptyList(), level)
		}

		val firstOptional = defaultParams.first()
		val remaining = defaultParams.drop(1)
		val indent = " ".repeat(level * 4)

		return buildString {
			appendLine("${indent}if (params.containsKey(\"${firstOptional.name}\")) {")
			val ifBranch = generateOptionalChain(helper, requiredParams + firstOptional, remaining, level + 1)
			appendLine(ifBranch)
			appendLine("$indent} else {")
			val elseBranch = generateOptionalChain(helper, requiredParams, remaining, level + 1)
			appendLine(elseBranch)
			appendLine("$indent}")
		}
	}

	private fun callFunction(
		fqFunctionName: String,
		requiredParams: List<ParamInfo>,
		optionalParams: List<ParamInfo>,
		level: Int,
	): String {
		val indent = " ".repeat(level * 4)
		val allParams = requiredParams + optionalParams
		val args =
			allParams.joinToString(",\n$indent    ") { param ->
				val suffix = if (param.hasDefault && !param.isNullable) "!!" else ""
				"${param.name} = obj.${param.name}$suffix"
			}
		return buildString {
			appendLine("$indent$fqFunctionName(")
			if (allParams.isNotEmpty()) {
				appendLine("$indent    $args")
			}
			append("$indent)")
		}
	}

	private fun KSFunctionDeclaration.toToolHelperOrNull(): ToolHelper? {
		val functionName = simpleName.asString()

		val paramInfos =
			parameters.mapIndexed { index, p ->
				val parameterName = p.name?.asString() ?: "arg$index"
				val parameterType = p.type.resolve()
				val fqnParameterType = parameterType.toFqnString()
				val hasDefault = p.hasDefault
				val isNullable = parameterType.isMarkedNullable
				val isRequired = !(hasDefault || isNullable)

				ParamInfo(
					name = parameterName,
					fqnType = fqnParameterType,
					readableType = parameterType.toString(),
					ksType = parameterType,
					isNullable = isNullable,
					hasDefault = hasDefault,
					isRequired = isRequired,
				)
			}

		val retFqn = returnType?.resolve()?.toFqnString() ?: returnType.toString()
		val originFiles = containingFile?.let { listOf(it) } ?: emptyList()

		return ToolHelper(
			ksFunction = this,
			functionName = functionName,
			fqName = qualifiedName?.asString() ?: "",
			params = paramInfos,
			returnTypeFqn = retFqn,
			returnTypeReadable = returnType.toString(),
			originatingFiles = originFiles,
		)
	}
}
