package sh.ondr.mcp4k.schema.roots

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.mcp4k.runtime.core.ClientApprovable
import sh.ondr.mcp4k.schema.core.JsonRpcRequest

@Serializable
@SerialName("roots/list")
data class ListRootsRequest(
	override val id: String,
) : JsonRpcRequest()

// Empty dummy class, just used to differentiate in the permissions callback
class ListRootsParams() : ClientApprovable
