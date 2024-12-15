package sh.ondr.kmcp.schema

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Role {
	@SerialName("assistant")
	ASSISTANT,

	@SerialName("user")
	USER,
}
