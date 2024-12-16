package sh.ondr.kmcp.runtime

import kotlinx.serialization.json.Json
import sh.ondr.kmcp.runtime.serialization.module

object KMCP {
	const val JSON_RPC_VERSION = "2.0"
	val json =
		Json {
			encodeDefaults = true
			explicitNulls = false
			isLenient = true
			classDiscriminator = "method"
			serializersModule = module
		}

	val toolDescriptions = mutableMapOf<String, String>()
}
