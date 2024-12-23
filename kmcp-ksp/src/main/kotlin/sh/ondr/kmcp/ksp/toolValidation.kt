package sh.ondr.kmcp.ksp

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Visibility

// TODO clean up this mess
internal fun KmcpProcessor.checkToolFunctions(tools: List<ToolHelper>): Boolean {
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
	val contentPkg = "$pkg.schema.content"
	val allowed = setOf(
		"$contentPkg.ToolContent",
		"$contentPkg.EmbeddedResourceContent",
		"$contentPkg.TextContent",
		"$contentPkg.ImageContent",
	)
	tools.filter { it.returnTypeFqn !in allowed }
		.forEach { tool ->
			logger.error(
				"KMCP error: @Tool function '${tool.functionName}' must return ToolContent or a sub-type. " +
					"Currently returns: ${tool.returnTypeReadable}. " +
					"Please change the return type to ToolContent.",
			)
			errorsFound = true
		}

	// 3. Parameter types must be supported
	for (tool in tools) {
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
	val disallowedModifiers = setOf(
		Modifier.INLINE,
		Modifier.PRIVATE,
		Modifier.PROTECTED,
		Modifier.INTERNAL,
		Modifier.ABSTRACT,
		Modifier.OPEN,
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
					"Only public, top-level, non-inline, non-abstract, non-internal functions are allowed.",
			)
			errorsFound = true
		}
	}

	return errorsFound
}

private fun KmcpProcessor.checkTypeSupported(type: KSType): String? {
	val primitiveTypes = setOf(
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
		val isSerializable = classDecl.annotations.any {
			it.annotationType.resolve().declaration.qualifiedName?.asString() == serializableFqn
		}
		if (isSerializable) {
			return null
		}

		return "Type '${classDecl.qualifiedName?.asString()}' is not a supported primitive, collection, enum, or @Serializable class."
	}

	return "Type '${qName ?: type.toString()}' is not supported."
}
