package sh.ondr.mcp4k.ksp.tools

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Visibility
import sh.ondr.mcp4k.ksp.Mcp4kProcessor
import kotlin.collections.filter

internal fun Mcp4kProcessor.checkToolFunctions(tools: List<ToolMeta>): Boolean {
	var errorsFound = false

	// 1. Unique tool names
	tools.groupBy { it.functionName }
		.filterValues { it.size > 1 }
		.forEach { (name, conflictingTools) ->
			val fqNames = conflictingTools.joinToString(", ") { it.fqName }
			conflictingTools.forEach { tool ->
				logger.error(
					"MCP4K error: Multiple @McpTool functions share the name '$name'. " +
						"Tool names must be unique. Conflicts: $fqNames",
					symbol = tool.ksFunction,
				)
			}
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
				"MCP4K error: @McpTool function '${tool.functionName}' must return ToolContent or a sub-type. " +
					"Currently returns: ${tool.returnTypeReadable}. " +
					"Please change the return type to ToolContent.",
				symbol = tool.ksFunction,
			)
			errorsFound = true
		}

	// 3. Limit non-required parameters
	val nonRequiredLimit = 7
	tools.filter { it.params.count { p -> !p.isRequired } > nonRequiredLimit }
		.forEach { tool ->
			logger.error(
				"MCP4K error: @McpTool function '${tool.functionName}' has more than $nonRequiredLimit non-required parameters. " +
					"Please reduce optional/nullable/default parameters to $nonRequiredLimit or fewer.",
				symbol = tool.ksFunction,
			)
			errorsFound = true
		}

	// 4. Must be top-level function
	tools.filter { it.ksFunction.parentDeclaration != null }
		.forEach { tool ->
			val parent = tool.ksFunction.parentDeclaration?.qualifiedName?.asString() ?: "unknown parent"
			logger.error(
				"MCP4K error: @McpTool function '${tool.functionName}' is defined inside a class or object ($parent). " +
					"@McpTool functions must be top-level. Move '${tool.functionName}' to file scope.",
				symbol = tool.ksFunction,
			)
			errorsFound = true
		}

	// 5. Must have no disallowed modifiers
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
				"MCP4K error: @McpTool function '${tool.functionName}' must be public. " +
					"Current visibility: $visibility. " +
					"Please ensure it's a top-level public function with no modifiers.",
				symbol = tool.ksFunction,
			)
			errorsFound = true
		}

		val foundDisallowed = tool.ksFunction.modifiers.filter { it in disallowedModifiers }
		if (foundDisallowed.isNotEmpty()) {
			logger.error(
				"MCP4K error: @McpTool function '${tool.functionName}' has disallowed modifiers: $foundDisallowed. " +
					"Only public, top-level, non-inline, non-abstract, non-internal functions are allowed.",
				symbol = tool.ksFunction,
			)
			errorsFound = true
		}
	}

	// 6. Check extension receivers (must be null or Server)
	tools.forEach { tool ->
		if (tool.isServerExtension) {
			tool.ksFunction.extensionReceiver?.resolve()?.declaration?.qualifiedName?.asString()?.let { receiverFq ->
				if (receiverFq != "sh.ondr.mcp4k.runtime.Server") {
					logger.error(
						"MCP4K error: @McpTool function '${tool.functionName}' is an extension function, but the receiver type is not 'Server'. " +
							"Please ensure the extension receiver is 'Server' or 'null'.",
						symbol = tool.ksFunction,
					)
				}
			}
		}
	}

	return errorsFound
}
