package sh.ondr.kmcp.ksp.prompts

import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import sh.ondr.kmcp.ksp.ParamInfo
import sh.ondr.kmcp.ksp.toFqnString

data class PromptMeta(
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

fun KSFunctionDeclaration.toPromptMeta(): PromptMeta {
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

	return PromptMeta(
		ksFunction = this,
		functionName = functionName,
		paramsClassName = functionName.replaceFirstChar { it.uppercase() } + "McpPromptParams",
		fqName = qualifiedName?.asString() ?: "",
		params = paramInfos,
		returnTypeFqn = returnType?.resolve()?.toFqnString() ?: returnType.toString(),
		returnTypeReadable = returnType.toString(),
		originatingFile = containingFile!!,
		kdoc = docString,
	)
}
