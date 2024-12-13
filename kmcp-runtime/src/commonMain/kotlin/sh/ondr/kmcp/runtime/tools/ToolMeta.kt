package sh.ondr.kmcp.runtime.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import sh.ondr.jsonschema.JsonSchema
import sh.ondr.kmcp.runtime.KMCP
import sh.ondr.kmcp.runtime.content.ToolContent
import kotlin.reflect.KClass

typealias GenericToolMeta = ToolMeta<*, *>

@Serializable
data class ToolMeta<T, R : ToolContent>(
	val name: String,
	val handler: T.() -> R,
	val paramsClass: KClass<*>,
	val resultClass: KClass<*>,
	val inputSchema: JsonSchema,
) {
	companion object {
		init {
			// <-- We are inserting IR-generated code here
		}
	}

	val toolSchema =
		buildJsonObject {
			put("name", name)
			put("description", KMCP.toolDescriptions[name])
			put("inputSchema", inputSchema.jsonObject)
		}

	fun call(any: Any?): ToolContent {
		@Suppress("UNCHECKED_CAST")
		val result = handler(any as T)
		return result
	}
}
