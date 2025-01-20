package sh.ondr.mcp4k.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import sh.ondr.mcp4k.ksp.prompts.PromptMeta
import sh.ondr.mcp4k.ksp.prompts.checkPromptFunctions
import sh.ondr.mcp4k.ksp.prompts.generatePromptHandlersFile
import sh.ondr.mcp4k.ksp.prompts.generatePromptParamsClass
import sh.ondr.mcp4k.ksp.prompts.toPromptMeta
import sh.ondr.mcp4k.ksp.tools.ToolMeta
import sh.ondr.mcp4k.ksp.tools.checkToolFunctions
import sh.ondr.mcp4k.ksp.tools.generateToolHandlersFile
import sh.ondr.mcp4k.ksp.tools.generateToolParamsClass
import sh.ondr.mcp4k.ksp.tools.toToolMeta
import kotlin.collections.isNotEmpty
import kotlin.collections.map

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

	val tools = mutableListOf<ToolMeta>()
	val prompts = mutableListOf<PromptMeta>()

	var isTest = false

	override fun process(resolver: Resolver): List<KSAnnotated> {
		if (resolver.getModuleName().getShortName().endsWith("_test")) {
			isTest = true
		}

		processTools(resolver)
		processPrompts(resolver)

		return emptyList()
	}

	@OptIn(KspExperimental::class)
	private fun processTools(resolver: Resolver) {
		val currentRoundTools: List<ToolMeta> = resolver
			.getSymbolsWithAnnotation(toolAnnoFqn)
			.filterIsInstance<KSFunctionDeclaration>()
			.map { it.toToolMeta() }
			.toList()

		val newAndExistingTools = tools + currentRoundTools
		val duplicates = newAndExistingTools
			.groupBy { it.functionName }
			.filterValues { it.size > 1 }

		if (duplicates.isNotEmpty()) {
			duplicates.forEach { (funcName, dupeList) ->
				dupeList.forEach { tool ->
					logger.error(
						"MCP4K error: multiple @McpTool functions share the name '$funcName'. " +
							"Conflicts: ${dupeList.joinToString { it.fqName }}",
						symbol = tool.ksFunction,
					)
				}
			}
			return
		}

		tools.addAll(currentRoundTools)
		currentRoundTools.forEach {
			generateToolParamsClass(it)
		}
	}

	private fun processPrompts(resolver: Resolver) {
		val currentRoundPrompts: List<PromptMeta> = resolver
			.getSymbolsWithAnnotation(promptAnnoFqn)
			.filterIsInstance<KSFunctionDeclaration>()
			.map { it.toPromptMeta() }
			.toList()

		val newAndExistingPrompts = prompts + currentRoundPrompts
		val duplicates = newAndExistingPrompts
			.groupBy { it.functionName }
			.filterValues { it.size > 1 }

		if (duplicates.isNotEmpty()) {
			duplicates.forEach { (funcName, dupeList) ->
				dupeList.forEach { prompt ->
					logger.error(
						"MCP4K error: multiple @McpPrompt functions share the name '$funcName'. " +
							"Conflicts: ${dupeList.joinToString { it.fqName }}",
						symbol = prompt.ksFunction,
					)
				}
			}
			return
		}

		prompts.addAll(currentRoundPrompts)
		currentRoundPrompts.forEach {
			generatePromptParamsClass(it)
		}
	}

	override fun finish() {
		generateToolHandlersFile()
		generatePromptHandlersFile()

		if (tools.isNotEmpty()) {
			val toolErrorsFound = checkToolFunctions(tools)
			if (toolErrorsFound) {
				logger.error("MCP4K Error: aborting code generation due to errors in @McpTool-annotated functions.")
				return
			}
		}
		
		if (prompts.isNotEmpty()) {
			val promptErrorsFound = checkPromptFunctions(prompts)
			if (promptErrorsFound) {
				logger.error("MCP4K Error: aborting code generation due to errors in @McpPrompt-annotated functions.")
				return
			}
		}

		generateInitializer()
	}
}
