package sh.ondr.mcp4k.ksp.tools

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Visibility
import sh.ondr.mcp4k.ksp.Mcp4kProcessor

/**
 * Validates a tool function and its metadata.
 * Returns true if valid, false if errors were found (errors are logged).
 */
internal fun Mcp4kProcessor.validateTool(
	ksFunction: KSFunctionDeclaration,
	toolMeta: ToolMeta,
): Boolean {
	var isValid = true

	// 1. Return type must be ToolContent or subtype
	val contentPkg = "$pkg.schema.content"
	val allowed = setOf(
		"$contentPkg.ToolContent",
		"$contentPkg.EmbeddedResourceContent",
		"$contentPkg.TextContent",
		"$contentPkg.ImageContent",
	)
	if (toolMeta.returnTypeFqn !in allowed) {
		logger.error(
			"MCP4K error: @McpTool function '${toolMeta.functionName}' must return ToolContent or a sub-type. " +
				"Currently returns: ${toolMeta.returnTypeReadable}. " +
				"Please change the return type to ToolContent.",
			symbol = ksFunction,
		)
		isValid = false
	}

	// 2. Limit non-required parameters
	val nonRequiredLimit = 7
	val nonRequiredCount = toolMeta.params.count { !it.isRequired }
	if (nonRequiredCount > nonRequiredLimit) {
		logger.error(
			"MCP4K error: @McpTool function '${toolMeta.functionName}' has more than $nonRequiredLimit non-required parameters. " +
				"Please reduce optional/nullable/default parameters to $nonRequiredLimit or fewer.",
			symbol = ksFunction,
		)
		isValid = false
	}

	// 3. Must be top-level function
	if (ksFunction.parentDeclaration != null) {
		val parent = ksFunction.parentDeclaration?.qualifiedName?.asString() ?: "unknown parent"
		logger.error(
			"MCP4K error: @McpTool function '${toolMeta.functionName}' is defined inside a class or object ($parent). " +
				"@McpTool functions must be top-level. Move '${toolMeta.functionName}' to file scope.",
			symbol = ksFunction,
		)
		isValid = false
	}

	// 4. Must have no disallowed modifiers
	val disallowedModifiers = setOf(
		Modifier.INLINE,
		Modifier.PRIVATE,
		Modifier.PROTECTED,
		Modifier.INTERNAL,
		Modifier.ABSTRACT,
		Modifier.OPEN,
	)

	val visibility = ksFunction.getVisibility()
	if (visibility != Visibility.PUBLIC) {
		logger.error(
			"MCP4K error: @McpTool function '${toolMeta.functionName}' must be public. " +
				"Current visibility: $visibility. " +
				"Please ensure it's a top-level public function with no modifiers.",
			symbol = ksFunction,
		)
		isValid = false
	}

	val foundDisallowed = ksFunction.modifiers.filter { it in disallowedModifiers }
	if (foundDisallowed.isNotEmpty()) {
		logger.error(
			"MCP4K error: @McpTool function '${toolMeta.functionName}' has disallowed modifiers: $foundDisallowed. " +
				"Only public, top-level, non-inline, non-abstract, non-internal functions are allowed.",
			symbol = ksFunction,
		)
		isValid = false
	}

	// 5. Check extension receivers (must be null or Server)
	if (toolMeta.isServerExtension) {
		ksFunction.extensionReceiver?.resolve()?.declaration?.qualifiedName?.asString()?.let { receiverFq ->
			if (receiverFq != "sh.ondr.mcp4k.runtime.Server") {
				logger.error(
					"MCP4K error: @McpTool function '${toolMeta.functionName}' is an extension function, but the receiver type is not 'Server'. " +
						"Please ensure the extension receiver is 'Server' or 'null'.",
					symbol = ksFunction,
				)
				isValid = false
			}
		}
	}

	return isValid
}
