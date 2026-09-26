package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.ApiErrorDto
import info.unterrainer.presserl.admin.api.ArticleDto
import info.unterrainer.presserl.admin.api.AuthorDto
import info.unterrainer.presserl.admin.api.ClientConfigDto
import info.unterrainer.presserl.admin.api.FieldErrorDto
import info.unterrainer.presserl.admin.api.MeDto
import info.unterrainer.presserl.admin.api.NewspaperDto
import info.unterrainer.presserl.admin.api.json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The examples of the REST contract (design D3 / ai/primer/endpoints.md). */
class DtoTest {

    @Test
    fun newspaper() {
        val dto = json.decodeFromString<NewspaperDto>(
            """
            {
              "name": "My Newspaper",
              "subtitle": "",
              "visibility": "public",
              "settings": {
                "retract.author-can-retract": true,
                "section.default": "General",
                "editor.level": "standard",
                "reader.text-size": "m",
                "media.max-size": "10M"
              }
            }
            """,
        )

        assertEquals("My Newspaper", dto.name)
        assertEquals("", dto.subtitle)
        assertEquals("public", dto.visibility)
        assertEquals(true, dto.settings.getValue("retract.author-can-retract").jsonPrimitive.boolean)
        assertEquals("10M", dto.settings.getValue("media.max-size").jsonPrimitive.content)
    }

    @Test
    fun clientConfig() {
        val dto = json.decodeFromString<ClientConfigDto>(
            """
            {
              "oidc": {
                "issuer": "https://auth.unterrainer.info/realms/presserl",
                "clientId": "presserl-admin",
                "scopes": ["openid", "profile"]
              }
            }
            """,
        )

        assertEquals("https://auth.unterrainer.info/realms/presserl", dto.oidc.issuer)
        assertEquals("presserl-admin", dto.oidc.clientId)
        assertEquals(listOf("openid", "profile"), dto.oidc.scopes)
    }

    @Test
    fun me() {
        val dto = json.decodeFromString<MeDto>("""{ "username": "papa", "displayName": "Papa", "roles": ["PUBLISHER"] }""")

        assertEquals(MeDto("papa", "Papa", listOf("PUBLISHER")), dto)
    }

    @Test
    fun additiveFieldsAreIgnored() {
        val dto = json.decodeFromString<MeDto>(
            """{ "username": "papa", "displayName": "Papa", "roles": [], "allowedActions": ["x"] }""",
        )

        assertEquals("papa", dto.username)
    }

    @Test
    fun article() {
        val dto = json.decodeFromString<ArticleDto>(ARTICLE)

        assertEquals(42, dto.id)
        assertEquals("PUBLISHED", dto.status)
        assertEquals(AuthorDto("papa", "Papa"), dto.author)
        assertEquals(2, dto.revision)
        assertEquals(1, dto.liveRevision)
        assertEquals(true, dto.hasUnpublishedChanges)
        assertEquals(5, dto.version)
        assertEquals("2026-09-26T10:01:00Z", dto.publishedAt)
        assertEquals("The pumpkin is huge", dto.headline)
        assertEquals(1, dto.body.getValue("version").jsonPrimitive.int)
        assertEquals(4, dto.body.getValue("blocks").jsonArray.size)
        assertEquals("subhead", dto.body.getValue("blocks").jsonArray[1].jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals(listOf("EDIT", "PUBLISH", "TAKE_OFFLINE"), dto.allowedActions)
    }

    @Test
    fun neverPublishedArticle() {
        val dto = json.decodeFromString<ArticleDto>(
            ARTICLE.replace("\"liveRevision\": 1", "\"liveRevision\": null")
                .replace("\"publishedAt\": \"2026-09-26T10:01:00Z\"", "\"publishedAt\": null"),
        )

        assertNull(dto.liveRevision)
        assertNull(dto.publishedAt)
    }

    @Test
    fun errorBody() {
        val dto = json.decodeFromString<ApiErrorDto>(
            """{ "errors": [ { "field": "body.blocks[0].type", "message": "unknown block type 'html'" },
                             { "field": null, "message": "only the author may edit this article" } ] }""",
        )

        assertEquals(
            listOf(
                FieldErrorDto("body.blocks[0].type", "unknown block type 'html'"),
                FieldErrorDto(null, "only the author may edit this article"),
            ),
            dto.errors,
        )
    }
}
