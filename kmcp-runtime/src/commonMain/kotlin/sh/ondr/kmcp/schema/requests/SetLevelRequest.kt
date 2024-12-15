package sh.ondr.kmcp.schema.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.logging.LoggingLevel

@Serializable
@SerialName("logging/setLevel")
data class SetLevelRequest(
	override val id: String,
	override val method: String = "logging/setLevel",
	val params: LoggingLevel,
) : JsonRpcRequest()
