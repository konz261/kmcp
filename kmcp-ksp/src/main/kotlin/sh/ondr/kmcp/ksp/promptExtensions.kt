package sh.ondr.kmcp.ksp

import com.google.devtools.ksp.symbol.KSFunctionDeclaration

// TODO merge duplicate
internal fun KSFunctionDeclaration.toPromptHelperOrNull(): PromptHelper? {
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

	val originFiles = containingFile?.let { listOf(it) } ?: emptyList()

	// Parse docstring if available
	val docString = this.docString
	val (mainDesc, paramDescriptions) = docString?.parseDescription(paramInfos.map { it.name }) ?: KDocDescription(null, emptyMap())

	// Assign parameter descriptions
	paramInfos.forEach { p ->
		p.description = paramDescriptions[p.name]
	}

	return PromptHelper(
		ksFunction = this,
		functionName = functionName,
		fqName = qualifiedName?.asString() ?: "",
		params = paramInfos,
		description = mainDesc,
		originatingFiles = originFiles,
	)
}
