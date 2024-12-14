package sh.ondr.kmcp.schema

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("method")
abstract class JsonRpcRequest : JsonRpcMessage() {
	abstract val id: String

	@SerialName("method")
	abstract val method: String
}
