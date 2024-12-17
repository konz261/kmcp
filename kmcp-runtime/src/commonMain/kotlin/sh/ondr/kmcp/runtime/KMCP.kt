package sh.ondr.kmcp.runtime

import kotlinx.serialization.json.Json
import sh.ondr.kmcp.runtime.serialization.module

const val JSON_RPC_VERSION = "2.0"
val kmcpJson =
	Json {
		encodeDefaults = true
		explicitNulls = false
		isLenient = true
		classDiscriminator = "method"
		serializersModule = module
	}

object KMCP {
	val toolDescriptions = mutableMapOf<String, String>()
}
