package sh.ondr.kmcp.runtime

import kotlinx.serialization.json.Json

object KMCP {
	val json =
		Json {
			explicitNulls = false
			isLenient = true
		}

	val toolDescriptions = mutableMapOf<String, String>()
}
