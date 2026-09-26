package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.ClientConfigDto
import info.unterrainer.presserl.admin.api.MeDto
import info.unterrainer.presserl.admin.api.NewspaperDto
import info.unterrainer.presserl.admin.api.json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
