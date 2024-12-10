package sh.ondr.kmcp.ksp

object TypeUtil {
	const val INT_INFO = "must be a 32-Bit Integer"
	const val LONG_INFO = "must be a 64-Bit Integer"

	fun String.addTypeHint(type: String): String {
		return if (isNotEmpty()) {
			"$this ($type)"
		} else {
			type.replaceFirstChar { it.uppercase() }
		}
	}
}
