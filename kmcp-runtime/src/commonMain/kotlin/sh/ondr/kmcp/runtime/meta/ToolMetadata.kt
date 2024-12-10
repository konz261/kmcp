package sh.ondr.kmcp.runtime.meta

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import sh.ondr.kmcp.runtime.tools.Tool
import sh.ondr.kmcp.runtime.tools.ToolInputSchema

data class ToolMetadata(
	val fqName: String,
	val name: String,
	val description: String?,
	val parameters: List<ParameterMetadata>,
	val returnType: String,
) {
	fun toTool(): Tool {
		val properties = mutableMapOf<String, JsonObject>()
		val requiredFields = mutableListOf<String>()

		parameters.forEach { param ->
			val jsonType =
				when (param.type) {
					"kotlin.String" -> "string"
					"kotlin.Int", "kotlin.Long", "kotlin.Double" -> "number"
					else -> "string" // Fallback, but ideally handle or error for unsupported types
				}

			properties[param.name] =
				buildJsonObject {
					put("type", JsonPrimitive(jsonType))
					param.description?.let { desc ->
						put("description", JsonPrimitive(desc))
					}
				}

			if (param.isOptional == false) {
				requiredFields.add(param.name)
			}
		}

		return Tool(
			name = name,
			description = description,
			inputSchema =
				ToolInputSchema(
					type = "object",
					properties = if (properties.isNotEmpty()) properties else null,
					required = if (requiredFields.isNotEmpty()) requiredFields else null,
				),
		)
	}
}
