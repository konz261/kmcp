package sh.ondr.kmcp.schema.completion

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.core.JsonRpcRequest

@Serializable
@SerialName("completion/complete")
data class CompleteRequest(
	override val id: String,
	val params: CompleteParams,
) : JsonRpcRequest()
