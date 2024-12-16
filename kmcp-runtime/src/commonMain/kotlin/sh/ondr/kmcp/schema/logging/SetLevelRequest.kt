package sh.ondr.kmcp.schema.logging

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.core.JsonRpcRequest

@Serializable
@SerialName("logging/setLevel")
data class SetLevelRequest(
	override val id: String,
	override val method: String = "logging/setLevel",
	val params: LoggingLevel,
) : JsonRpcRequest()
