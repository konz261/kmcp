package sh.ondr.kmcp.schema.completion

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.JsonRpcRequest

@Serializable
@SerialName("completion/complete")
data class CompleteRequest(
	override val id: String,
	override val method: String = "completion/complete",
	val params: CompleteParams,
) : JsonRpcRequest()
