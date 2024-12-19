package sh.ondr.kmcp.ksp

import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

data class PromptHelper(
	val ksFunction: KSFunctionDeclaration,
	val functionName: String,
	val fqName: String,
	val params: List<ParamInfo>,
	var description: String? = null,
	val originatingFiles: List<KSFile>,
)
