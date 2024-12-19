package sh.ondr.kmcp.schema.logging

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.schema.core.JsonRpcRequest

@Serializable
@SerialName("logging/setLevel")
data class SetLoggingLevelRequest(
	override val id: String,
	val params: SetLoggingLevelParams,
) : JsonRpcRequest() {
	@Serializable
	data class SetLoggingLevelParams(
		val level: LoggingLevel,
		val _meta: Map<String, JsonElement>? = null,
	)
}
