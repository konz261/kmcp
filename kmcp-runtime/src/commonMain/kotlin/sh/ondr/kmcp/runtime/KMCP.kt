package sh.ondr.kmcp.runtime

import kotlinx.serialization.json.Json

object KMCP {
	const val JSON_RPC_VERSION = "2.0"
	val json =
		Json {
			encodeDefaults = true
			explicitNulls = false
			isLenient = true
		}

	val toolDescriptions = mutableMapOf<String, String>()
}
