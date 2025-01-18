package sh.ondr.kmcp.schema.roots

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.runtime.core.ClientApprovable
import sh.ondr.kmcp.schema.core.JsonRpcRequest

@Serializable
@SerialName("roots/list")
data class ListRootsRequest(
	override val id: String,
) : JsonRpcRequest()

// Empty dummy class, just used to differentiate in the permissions callback
class ListRootsParams() : ClientApprovable
