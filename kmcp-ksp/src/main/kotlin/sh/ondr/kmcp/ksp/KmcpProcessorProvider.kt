package sh.ondr.kmcp.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class KmcpProcessorProvider : SymbolProcessorProvider {
	override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
		return KmcpProcessor(
			codeGenerator = environment.codeGenerator,
			logger = environment.logger,
			options = environment.options,
		)
	}
}
