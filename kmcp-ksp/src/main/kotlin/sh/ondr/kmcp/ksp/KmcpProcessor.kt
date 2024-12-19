package sh.ondr.kmcp.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

class KmcpProcessor(
	val codeGenerator: CodeGenerator,
	val logger: KSPLogger,
	private val options: Map<String, String>,
) : SymbolProcessor {
	val pkg = "sh.ondr.kmcp"
	val toolAnnoFqn = "$pkg.runtime.annotation.Tool"
	val promptAnnoFqn = "$pkg.runtime.annotation.Prompt"
	val generatedPkg = "$pkg.generated"

	val collectedTools = mutableListOf<ToolHelper>()
	val collectedPrompts = mutableListOf<PromptHelper>()

	override fun process(resolver: Resolver): List<KSAnnotated> {
		val toolFunctions = resolver
			.getSymbolsWithAnnotation(toolAnnoFqn)
			.filterIsInstance<KSFunctionDeclaration>()
			.toList()

		if (toolFunctions.isNotEmpty()) {
			val newTools = toolFunctions.mapNotNull { it.toToolHelperOrNull() }
			collectedTools.addAll(newTools)
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
		if (collectedPrompts.isNotEmpty()) {
			generatePromptFiles()
			logger.warn("collected prompts: $collectedPrompts")
		}

		if (collectedTools.isNotEmpty()) {
			val errorsFound = checkToolFunctions(collectedTools)
			if (errorsFound) {
				logger.error("KMCP Error: aborting code generation due to errors in @Tool-annotated functions.")
				return
			}
			generateToolFiles()
			generateInitializer()
		}
	}
}
