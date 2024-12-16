package sh.ondr.kmcp.schema.roots

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.core.JsonRpcRequest

@Serializable
@SerialName("roots/list")
data class ListRootsRequest(
	override val id: String,
	override val method: String = "roots/list",
) : JsonRpcRequest()
