package sh.ondr.mcp4k.fileprovider

fun interface MimeTypeDetector {
	/**
	 * Given a filename (or path segment), return its MIME type, e.g. "text/plain"
	 * or "image/png".
	 */
	fun detect(pathName: String): String
}
