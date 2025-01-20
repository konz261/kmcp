@file:OptIn(ExperimentalEncodingApi::class)

package sh.ondr.mcp4k.runtime.resources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use
import sh.ondr.mcp4k.schema.resources.Resource
import sh.ondr.mcp4k.schema.resources.ResourceContents
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A [ResourceProvider] that exposes a finite, discrete set of files (by relative path)
 * from a given [rootDir]. It does NOT return any resource templates.
 *
 * Example usage:
 *
 * ```
 * val fileSystem: FileSystem = FileSystem.SYSTEM
 * val provider = DiscreteFileProvider(
 *     fileSystem = fileSystem,
 *     rootDir = "/some/local/folder".toPath(),
 *     knownFiles = listOf("notes.txt", "report.pdf")
 * )
 * ```
 *
 * Once created, [listResources] will show the above files, each accessible via a `file://`
 * URI like `file://notes.txt`.
 *
 * If you need to add or remove files at runtime, call [addFile] or [removeFile].
 */
class DiscreteFileProvider(
	private val fileSystem: FileSystem,
	private val rootDir: Path,
	initialFiles: List<File> = emptyList(),
	private val mimeTypeDetector: MimeTypeDetector = MimeTypeDetector { "text/plain" },
) : ResourceProvider() {
	override val supportsSubscriptions: Boolean = true

	private val files = initialFiles.toMutableList()

	/**
	 * Lists the currently known, discrete resources. Each resource is associated with a
	 * `file://relativePath` URI.
	 */
	override suspend fun listResources(): List<Resource> {
		return files.map { discreteFile ->
			// If user didn't provide a custom name, fallback to the actual file name from the path
			val resolvedPath = rootDir.resolve(discreteFile.relativePath)
			val fallbackName = resolvedPath.name
			val fallbackDesc = "File at ${discreteFile.relativePath}"
			val fallbackMime = mimeTypeDetector.detect(fallbackName)

			Resource(
				uri = "file://${discreteFile.relativePath}",
				name = discreteFile.name ?: fallbackName,
				description = discreteFile.description ?: fallbackDesc,
				mimeType = discreteFile.mimeType ?: fallbackMime,
			)
		}
	}

	/**
	 * Reads file contents if [uri] starts with `file://` and matches one of the known files.
	 * If the file doesn't exist or is a directory, returns null.
	 */
	override suspend fun readResource(uri: String): ResourceContents? =
		withContext(Dispatchers.Default) {
			// Expecting uri like "file://someRelativePath"
			if (!uri.startsWith("file://")) return@withContext null
			val relativePath = uri.removePrefix("file://")

			// If it’s not in our known list, treat it as not found
			val knownEntry = files.find { it.relativePath == relativePath }
				?: return@withContext null

			val fullPath = rootDir.resolve(relativePath)
			if (!fileSystem.exists(fullPath)) return@withContext null
			if (fileSystem.metadata(fullPath).isDirectory) return@withContext null

			// Read file
			val source = fileSystem.source(fullPath)
			val data = source.buffer().use { it.readByteArray() }

			// Either use the knownEntry.mimeType or guess
			val fallbackName = fullPath.name
			val mimeType = knownEntry.mimeType ?: mimeTypeDetector.detect(fallbackName)

			// Simple text vs. blob check
			if (mimeType.startsWith("text")) {
				val text = runCatching { data.decodeToString() }.getOrNull() ?: return@withContext null
				ResourceContents.Text(
					uri = uri,
					mimeType = mimeType,
					text = text,
				)
			} else {
				ResourceContents.Blob(
					uri = uri,
					mimeType = mimeType,
					blob = Base64.encode(data),
				)
			}
		}

	/**
	 * Adds a file to the known list, triggering onResourcesListChanged() if
	 * the file wasn't in the list before.
	 *
	 * @return `true` if the file was added, `false` if it was already in the list.
	 */
	suspend fun addFile(file: File): Boolean {
		return if (files.none { it.relativePath == file.relativePath }) {
			files += file
			onResourcesListChanged()
			true
		} else {
			false
		}
	}

	/**
	 * Removes a file from the known list, triggering onResourcesListChanged() if removed.
	 *
	 * @return `true` if the file was removed, `false` if it wasn't in the list.
	 */
	suspend fun removeFile(relativePath: String): Boolean {
		val removed = files.removeAll { it.relativePath == relativePath }
		return if (removed) {
			onResourcesListChanged()
			true
		} else {
			false
		}
	}
}
