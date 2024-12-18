package sh.ondr.kmcp.ksp

import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

data class ToolHelper(
	val ksFunction: KSFunctionDeclaration,
	val functionName: String,
	val fqName: String,
	val params: List<ParamInfo>,
	val returnTypeFqn: String,
	val returnTypeReadable: String,
	val originatingFiles: List<KSFile>,
)
