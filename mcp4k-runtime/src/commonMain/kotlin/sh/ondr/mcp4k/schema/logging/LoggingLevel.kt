package sh.ondr.mcp4k.schema.logging

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LoggingLevel {
	@SerialName("debug")
	DEBUG,

	@SerialName("info")
	INFO,

	@SerialName("notice")
	NOTICE,

	@SerialName("warning")
	WARNING,

	@SerialName("error")
	ERROR,

	@SerialName("critical")
	CRITICAL,

	@SerialName("alert")
	ALERT,

	@SerialName("emergency")
	EMERGENCY,
}
