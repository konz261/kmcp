package sh.ondr.mcp4k.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class Mcp4kProcessorProvider : SymbolProcessorProvider {
	override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
		return Mcp4kProcessor(
			codeGenerator = environment.codeGenerator,
			logger = environment.logger,
			options = environment.options,
		)
	}
}
