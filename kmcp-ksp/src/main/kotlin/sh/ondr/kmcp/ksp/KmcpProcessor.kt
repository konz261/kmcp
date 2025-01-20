package sh.ondr.kmcp.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import sh.ondr.kmcp.ksp.prompts.PromptMeta
import sh.ondr.kmcp.ksp.prompts.checkPromptFunctions
import sh.ondr.kmcp.ksp.prompts.generatePromptHandlersFile
import sh.ondr.kmcp.ksp.prompts.generatePromptParamsClass
import sh.ondr.kmcp.ksp.prompts.toPromptMeta
import sh.ondr.kmcp.ksp.tools.ToolMeta
import sh.ondr.kmcp.ksp.tools.checkToolFunctions
import sh.ondr.kmcp.ksp.tools.generateToolHandlersFile
import sh.ondr.kmcp.ksp.tools.generateToolParamsClass
import sh.ondr.kmcp.ksp.tools.toToolMeta
import kotlin.collections.isNotEmpty
import kotlin.collections.map

@OptIn(KspExperimental::class)
class KmcpProcessor(
	val codeGenerator: CodeGenerator,
	val logger: KSPLogger,
	private val options: Map<String, String>,
) : SymbolProcessor {
	val pkg = "sh.ondr.kmcp"

	val toolAnnoFqn = "$pkg.runtime.annotation.McpTool"
	val promptAnnoFqn = "$pkg.runtime.annotation.McpPrompt"

	val kmcpParamsPackage = "$pkg.generated.params"
	val kmcpHandlersPackage = "$pkg.generated.handlers"

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
						"KMCP error: multiple @McpTool functions share the name '$funcName'. " +
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
						"KMCP error: multiple @McpPrompt functions share the name '$funcName'. " +
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
				logger.error("KMCP Error: aborting code generation due to errors in @McpTool-annotated functions.")
				return
			}
		}
		
		if (prompts.isNotEmpty()) {
			val promptErrorsFound = checkPromptFunctions(prompts)
			if (promptErrorsFound) {
				logger.error("KMCP Error: aborting code generation due to errors in @McpPrompt-annotated functions.")
				return
			}
		}

		generateInitializer()
	}
}
