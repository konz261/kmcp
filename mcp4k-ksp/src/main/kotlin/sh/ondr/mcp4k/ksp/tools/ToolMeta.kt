package sh.ondr.mcp4k.ksp.tools

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import sh.ondr.mcp4k.ksp.ParamInfo
import sh.ondr.mcp4k.ksp.toFqnString

data class ToolMeta(
	val functionName: String,
	val fqName: String,
	val params: List<ParamInfo>,
	val paramsClassName: String,
	val returnTypeFqn: String,
	val returnTypeReadable: String,
	val originatingFilePath: String,
	val kdoc: String? = null,
	val isServerExtension: Boolean,
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
			fqnTypeNonNullable = parameterType.makeNotNullable().toFqnString(),
			readableType = parameterType.toString(),
			isNullable = isNullable,
			hasDefault = hasDefault,
			isRequired = isRequired,
		)
	}

	return ToolMeta(
		functionName = functionName,
		paramsClassName = functionName.replaceFirstChar { it.uppercase() } + "McpToolParams",
		fqName = qualifiedName?.asString() ?: "",
		params = paramInfos,
		returnTypeFqn = returnType?.resolve()?.toFqnString() ?: returnType.toString(),
		returnTypeReadable = returnType.toString(),
		originatingFilePath = containingFile!!.filePath,
		kdoc = docString,
		isServerExtension = extensionReceiver != null, // We check for type in validation
	)
}
