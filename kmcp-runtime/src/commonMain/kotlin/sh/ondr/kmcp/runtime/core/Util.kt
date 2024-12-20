package sh.ondr.kmcp.runtime.core

import sh.ondr.kmcp.schema.content.TextContent
import sh.ondr.kmcp.schema.core.Annotations

fun String.toTextContent(annotations: Annotations? = null) = TextContent(this, annotations)
