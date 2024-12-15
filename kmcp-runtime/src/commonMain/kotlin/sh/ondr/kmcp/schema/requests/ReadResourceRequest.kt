package sh.ondr.kmcp.schema.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.requests.params.ReadResourceParams

@Serializable
@SerialName("resources/read")
data class ReadResourceRequest(
	override val id: String,
	override val method: String = "resources/read",
	val params: ReadResourceParams,
) : JsonRpcRequest()
