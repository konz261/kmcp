package sh.ondr.kmcp.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import sh.ondr.kmcp.ksp.tools.ToolMeta
import sh.ondr.kmcp.ksp.tools.generateToolHandlersFile
import sh.ondr.kmcp.ksp.tools.generateToolParamsClass
import sh.ondr.kmcp.ksp.tools.toToolMeta
import kotlin.collections.isNotEmpty
import kotlin.collections.mapNotNull

class KmcpProcessor(
	val codeGenerator: CodeGenerator,
	val logger: KSPLogger,
	private val options: Map<String, String>,
) : SymbolProcessor {
	val pkg = "sh.ondr.kmcp"
	val toolAnnoFqn = "$pkg.runtime.annotation.McpTool"
	val promptAnnoFqn = "$pkg.runtime.annotation.McpPrompt"

	// TODO remove this intermediate variable
	val kmcpParamsPackage = "$pkg.generated.params"
	val kmcpHandlersPackage = "$pkg.generated.handlers"
	val kmcpInitializerPackage = "$pkg.generated.initializer"

	val tools = mutableListOf<ToolMeta>()
	val collectedPrompts = mutableListOf<PromptHelper>()

	override fun process(resolver: Resolver): List<KSAnnotated> {
		val currentRoundTools: List<ToolMeta> = resolver
			.getSymbolsWithAnnotation(toolAnnoFqn)
			.filterIsInstance<KSFunctionDeclaration>()
			.map { it.toToolMeta() }
			.toList()

		// Validate that function names are unique
		val currentDuplicated = currentRoundTools.groupBy { it.functionName }.any { it.value.size > 1 }
		val globalDuplicated = tools.map { it.functionName }.intersect(currentRoundTools.map { it.functionName }).isNotEmpty()
		if (currentDuplicated || globalDuplicated) {
			// TODO reference specific duplications
			logger.error("@McpTool function names must be unique")
			return emptyList()
		}

		tools.addAll(currentRoundTools)
		currentRoundTools.forEach {
			generateToolParamsClass(it)
		}

		val promptFunctions = resolver
			.getSymbolsWithAnnotation(promptAnnoFqn)
			.filterIsInstance<KSFunctionDeclaration>()
			.toList()

		if (promptFunctions.isNotEmpty()) {
			val newPrompts = promptFunctions.mapNotNull { it.toPromptHelperOrNull() }
			collectedPrompts.addAll(newPrompts)
		}

		return emptyList()
	}

	override fun finish() {
		generateToolHandlersFile()
		if (collectedPrompts.isNotEmpty()) {
			generatePromptFiles()
		}

		if (tools.isNotEmpty()) {
			val errorsFound = checkToolFunctions(tools)
			if (errorsFound) {
				logger.error("KMCP Error: aborting code generation due to errors in @McpTool-annotated functions.")
				return
			}
			generateInitializer()
		}
	}
}
