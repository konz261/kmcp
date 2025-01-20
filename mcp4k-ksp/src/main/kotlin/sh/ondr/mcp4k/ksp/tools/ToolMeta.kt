package sh.ondr.mcp4k.ksp.tools

import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import sh.ondr.mcp4k.ksp.ParamInfo
import sh.ondr.mcp4k.ksp.toFqnString

data class ToolMeta(
	val ksFunction: KSFunctionDeclaration,
	val functionName: String,
	val fqName: String,
	val params: List<ParamInfo>,
	val paramsClassName: String,
	val returnTypeFqn: String,
	val returnTypeReadable: String,
	val originatingFile: KSFile,
	val kdoc: String? = null,
)

fun KSFunctionDeclaration.toToolMeta(): ToolMeta {
	val functionName = simpleName.asString()
	val paramInfos = parameters.mapIndexed { index, p ->
		val parameterName = p.name?.asString() ?: "arg$index"
		val parameterType = p.type.resolve()
		val fqnParameterType = parameterType.toFqnString()
		val hasDefault = p.hasDefault
		val isNullable = parameterType.isMarkedNullable
		val isRequired = !(hasDefault || isNullable)

		ParamInfo(
			name = parameterName,
			fqnType = fqnParameterType,
			readableType = parameterType.toString(),
			ksType = parameterType,
			isNullable = isNullable,
			hasDefault = hasDefault,
			isRequired = isRequired,
		)
	}

	return ToolMeta(
		ksFunction = this,
		functionName = functionName,
		paramsClassName = functionName.replaceFirstChar { it.uppercase() } + "McpToolParams",
		fqName = qualifiedName?.asString() ?: "",
		params = paramInfos,
		returnTypeFqn = returnType?.resolve()?.toFqnString() ?: returnType.toString(),
		returnTypeReadable = returnType.toString(),
		originatingFile = containingFile!!,
		kdoc = docString,
	)
}
