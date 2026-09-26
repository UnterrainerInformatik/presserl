@file:OptIn(ExperimentalSerializationApi::class)

package info.unterrainer.presserl.admin.article

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Text of a paragraph, quote or list item; [text] is never empty and may contain `\n`. */
@Serializable
data class Run(val text: String, val bold: Boolean = false)

/** A block of body format v1; the JSON field `type` names the variant. */
@Serializable
sealed interface Block {

    @Serializable
    @SerialName("paragraph")
    data class Paragraph(val content: List<Run>) : Block

    @Serializable
    @SerialName("subhead")
    data class Subhead(val text: String) : Block

    @Serializable
    @SerialName("quote")
    data class Quote(val content: List<Run>) : Block

    /** [items] holds at least one item. */
    @Serializable
    @SerialName("list")
    data class BulletList(val items: List<List<Run>>) : Block
}

/** Article body in format v1 (`{"version": 1, "blocks": [...]}`), see spec `articles`. */
@Serializable
data class Body(
    @EncodeDefault val version: Int = FORMAT_VERSION,
    val blocks: List<Block>,
) {
    fun toJson(): JsonObject = bodyJson.encodeToJsonElement(serializer(), this).jsonObject

    companion object {
        const val FORMAT_VERSION = 1

        fun fromJson(json: JsonObject): Body = bodyJson.decodeFromJsonElement(serializer(), json)
    }
}

/** `bold: false` is the default and never sent. */
private val bodyJson = Json {
    encodeDefaults = false
    classDiscriminator = "type"
}
