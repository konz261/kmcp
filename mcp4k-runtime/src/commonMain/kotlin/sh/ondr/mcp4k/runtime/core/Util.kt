package sh.ondr.mcp4k.runtime.core

import sh.ondr.mcp4k.schema.content.TextContent
import sh.ondr.mcp4k.schema.core.Annotations

fun String.toTextContent(annotations: Annotations? = null) = TextContent(this, annotations)
