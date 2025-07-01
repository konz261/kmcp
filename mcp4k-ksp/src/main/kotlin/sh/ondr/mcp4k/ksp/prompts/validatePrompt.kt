package sh.ondr.mcp4k.ksp.prompts

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Visibility
import sh.ondr.mcp4k.ksp.Mcp4kProcessor

/**
 * Validates a prompt function and its metadata.
 * Returns true if valid, false if errors were found (errors are logged).
 */
internal fun Mcp4kProcessor.validatePrompt(
	ksFunction: KSFunctionDeclaration,
	promptMeta: PromptMeta,
): Boolean {
	var isValid = true

	// 1. Return type must be GetPromptResult
	if (promptMeta.returnTypeFqn != "sh.ondr.mcp4k.schema.prompts.GetPromptResult") {
		logger.error(
			"MCP4K error: @McpPrompt function '${promptMeta.functionName}' must return GetPromptResult. " +
				"Currently returns: ${promptMeta.returnTypeReadable}. ",
			symbol = ksFunction,
		)
		isValid = false
	}

	// 2. Parameter types must be String or String?
	promptMeta.params
		.filterNot { param ->
			param.fqnType == "kotlin.String" || param.fqnType == "kotlin.String?"
		}.forEach { param ->
			logger.error(
				"MCP4K error: @McpPrompt function '${promptMeta.functionName}' has a parameter '${param.name}' " +
					"with unsupported type '${param.readableType}'. " +
					"Only String (or String?) is allowed for prompt parameters.",
				symbol = ksFunction,
			)
			isValid = false
		}

	// 3. Limit non-required parameters
	val nonRequiredLimit = 7
	val nonRequiredCount = promptMeta.params.count { !it.isRequired }
	if (nonRequiredCount > nonRequiredLimit) {
		logger.error(
			"MCP4K error: @McpPrompt function '${promptMeta.functionName}' has more than $nonRequiredLimit non-required parameters. " +
				"Please reduce optional/nullable/default parameters to $nonRequiredLimit or fewer.",
			symbol = ksFunction,
		)
		isValid = false
	}

	// 4. Must be top-level function
	if (ksFunction.parentDeclaration != null) {
		val parent = ksFunction.parentDeclaration?.qualifiedName?.asString() ?: "unknown parent"
		logger.error(
			"MCP4K error: @McpPrompt function '${promptMeta.functionName}' is defined inside a class or object ($parent). " +
				"@McpPrompt functions must be top-level. Move '${promptMeta.functionName}' to file scope.",
			symbol = ksFunction,
		)
		isValid = false
	}

	// 5. Must have no disallowed modifiers and be public
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
			"MCP4K error: @McpPrompt function '${promptMeta.functionName}' must be public. " +
				"Current visibility: $visibility. " +
				"Please ensure it's a top-level public function with no additional modifiers.",
			symbol = ksFunction,
		)
		isValid = false
	}

	val foundDisallowed = ksFunction.modifiers.filter { it in disallowedModifiers }
	if (foundDisallowed.isNotEmpty()) {
		logger.error(
			"MCP4K error: @McpPrompt function '${promptMeta.functionName}' has disallowed modifiers: $foundDisallowed. " +
				"Only public, top-level, non-inline, non-abstract, non-internal functions are allowed.",
			symbol = ksFunction,
		)
		isValid = false
	}

	// 6. Check extension receivers (must be null or Server)
	if (promptMeta.isServerExtension) {
		ksFunction.extensionReceiver?.resolve()?.declaration?.qualifiedName?.asString()?.let { receiverFq ->
			if (receiverFq != "sh.ondr.mcp4k.runtime.Server") {
				logger.error(
					"MCP4K error: @McpPrompt function '${promptMeta.functionName}' is an extension function, but the receiver type is not 'Server'. " +
						"Please ensure the extension receiver is 'Server' or 'null'.",
					symbol = ksFunction,
				)
				isValid = false
			}
		}
	}

	return isValid
}
