@file:OptIn(ExperimentalSerializationApi::class)

package sh.ondr.kmcp.schema.content

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("type")
sealed interface PromptContent : Content
