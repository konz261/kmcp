package sh.ondr.kmcp.runtime.base

import kotlinx.serialization.Serializable

@Serializable
enum class Role {
	USER,
	ASSISTANT,
}
