package sh.ondr.mcp4k.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import sh.ondr.mcp4k.ksp.prompts.PromptMeta
import sh.ondr.mcp4k.ksp.prompts.generatePromptHandlersFile
import sh.ondr.mcp4k.ksp.prompts.generatePromptParamsClass
import sh.ondr.mcp4k.ksp.prompts.toPromptMeta
import sh.ondr.mcp4k.ksp.prompts.validatePrompt
import sh.ondr.mcp4k.ksp.tools.ToolMeta
import sh.ondr.mcp4k.ksp.tools.generateToolHandlersFile
import sh.ondr.mcp4k.ksp.tools.generateToolParamsClass
import sh.ondr.mcp4k.ksp.tools.toToolMeta
import sh.ondr.mcp4k.ksp.tools.validateTool
import kotlin.collections.isNotEmpty

@OptIn(KspExperimental::class)
class Mcp4kProcessor(
	val codeGenerator: CodeGenerator,
	val logger: KSPLogger,
	private val options: Map<String, String>,
) : SymbolProcessor {
	val pkg = "sh.ondr.mcp4k"

	val toolAnnoFqn = "$pkg.runtime.annotation.McpTool"
	val promptAnnoFqn = "$pkg.runtime.annotation.McpPrompt"

	val mcp4kParamsPackage = "$pkg.generated.params"
	val mcp4kHandlersPackage = "$pkg.generated.handlers"

	// Collections that survive across rounds - only plain data, no KS* objects!
	private val processedFunctions = mutableSetOf<String>() // FQNs only
	private val generatedParams = mutableSetOf<String>() // class simple names

	// These will be rebuilt each round from generated params
	val tools = mutableListOf<ToolMeta>()
	val prompts = mutableListOf<PromptMeta>()

	val isTest = options["isTestSet"]?.toBoolean() ?: false

	override fun process(resolver: Resolver): List<KSAnnotated> {
		processTools(resolver)
		processPrompts(resolver)

		return emptyList()
	}

	@OptIn(KspExperimental::class)
	private fun processTools(resolver: Resolver) {
		resolver.getSymbolsWithAnnotation(toolAnnoFqn)
			.filterIsInstance<KSFunctionDeclaration>()
			.forEach { ksFunction ->
				val fqName = ksFunction.qualifiedName?.asString() ?: return@forEach
				
				// Skip if already processed in a previous round
				if (fqName in processedFunctions) {
					return@forEach
				}
				
				val toolMeta = ksFunction.toToolMeta()
				
				// Check for duplicate names
				val existingTool = tools.find { it.functionName == toolMeta.functionName }
				if (existingTool != null) {
					logger.error(
						"MCP4K error: multiple @McpTool functions share the name '${toolMeta.functionName}'. " +
							"Conflicts: ${existingTool.fqName}, ${toolMeta.fqName}",
						symbol = ksFunction,
					)
					return@forEach
				}
				
				// Validate the tool
				if (!validateTool(ksFunction, toolMeta)) {
					return@forEach // Skip invalid tools
				}
				
				// Add to list
				tools.add(toolMeta)
				
				// Generate param class immediately with proper dependencies
				generateToolParamsClass(toolMeta, ksFunction.containingFile!!)
				generatedParams.add(toolMeta.paramsClassName)
				
				// Mark as processed
				processedFunctions.add(fqName)
			}
	}

	private fun processPrompts(resolver: Resolver) {
		resolver.getSymbolsWithAnnotation(promptAnnoFqn)
			.filterIsInstance<KSFunctionDeclaration>()
			.forEach { ksFunction ->
				val fqName = ksFunction.qualifiedName?.asString() ?: return@forEach
				
				// Skip if already processed in a previous round
				if (fqName in processedFunctions) {
					return@forEach
				}
				
				val promptMeta = ksFunction.toPromptMeta()
				
				// Check for duplicate names
				val existingPrompt = prompts.find { it.functionName == promptMeta.functionName }
				if (existingPrompt != null) {
					logger.error(
						"MCP4K error: multiple @McpPrompt functions share the name '${promptMeta.functionName}'. " +
							"Conflicts: ${existingPrompt.fqName}, ${promptMeta.fqName}",
						symbol = ksFunction,
					)
					return@forEach
				}
				
				// Validate the prompt
				if (!validatePrompt(ksFunction, promptMeta)) {
					return@forEach // Skip invalid prompts
				}
				
				// Add to list
				prompts.add(promptMeta)
				
				// Generate param class immediately with proper dependencies
				generatePromptParamsClass(promptMeta, ksFunction.containingFile!!)
				generatedParams.add(promptMeta.paramsClassName)
				
				// Mark as processed
				processedFunctions.add(fqName)
			}
	}

	override fun finish() {
		// Only generate aggregated files (handlers, initializer) if we have any tools or prompts
		if (tools.isNotEmpty() || prompts.isNotEmpty()) {
			// Generate handler files
			generateToolHandlersFile()
			generatePromptHandlersFile()
			generateInitializer()
		}
	}
}
