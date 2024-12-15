package sh.ondr.kmcp.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

class KmcpProcessor(
	private val codeGenerator: CodeGenerator,
	private val logger: KSPLogger,
	private val options: Map<String, String>,
) : SymbolProcessor {
	private val pkg = "sh.ondr.kmcp"
	private val toolAnnoFqn = "$pkg.runtime.annotation.Tool"

	override fun process(resolver: Resolver): List<KSAnnotated> {
		val toolFunctions =
			resolver.getSymbolsWithAnnotation(toolAnnoFqn)
				.filterIsInstance<KSFunctionDeclaration>()
				.toList()

		generateKmcpToolRegistryInitializer(toolFunctions)
		return emptyList()
	}

	private fun generateKmcpToolRegistryInitializer(toolMetadataList: List<KSFunctionDeclaration>) {
		val packageName = "$pkg.generated"
		val fileName = "KmcpGeneratedToolRegistryInitializer"

		data class ToolInfo(
			val name: String,
			val description: String? = null,
		)

		val tools =
			toolMetadataList.map { function ->
				val toolAnno = function.annotations.find { it.isToolAnno() }
				ToolInfo(
					name = function.simpleName.asString(),
					description = toolAnno!!.getStringArgument("description"),
				)
			}

		val fileContent =
			buildString {
				appendLine("package $packageName")
				appendLine()
				appendLine("import $pkg.runtime.KMCP")
				appendLine()
				appendLine("object $fileName {")
				appendLine("    init {")
				tools
					.filter { it.description!!.isNotEmpty() }
					.forEach { (name, description) ->
						appendLine("        KMCP.toolDescriptions[\"$name\"] = \"$description\"")
					}
				appendLine("    }")
				appendLine("}")
			}

		try {
			val file =
				codeGenerator.createNewFile(
					Dependencies(aggregating = false),
					packageName,
					fileName,
				)
			file.write(fileContent.toByteArray())
			file.close()
			logger.warn("Generated file \"$fileName\" with ${tools.size} tools")
		} catch (e: FileAlreadyExistsException) {
			// KSP running multiple times... ignore
		}
	}

	fun KSAnnotation.isToolAnno() = annotationType.resolve().declaration.qualifiedName?.asString() == toolAnnoFqn

	fun KSAnnotation.getStringArgument(name: String): String? = arguments.find { it.name?.asString() == name }?.value as? String
}
