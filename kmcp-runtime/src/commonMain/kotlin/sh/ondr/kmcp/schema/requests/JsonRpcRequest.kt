package sh.ondr.kmcp.schema.requests

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import sh.ondr.kmcp.schema.JsonRpcMessage

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("method")
sealed class JsonRpcRequest : JsonRpcMessage() {
	abstract val id: String
	abstract val method: String
}
