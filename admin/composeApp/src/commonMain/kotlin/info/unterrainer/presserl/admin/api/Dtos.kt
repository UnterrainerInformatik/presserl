package info.unterrainer.presserl.admin.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** `GET /api/newspaper` */
@Serializable
data class NewspaperDto(
    val name: String,
    val subtitle: String,
    val visibility: String,
    val settings: JsonObject,
)

/** `GET /api/client-config` */
@Serializable
data class ClientConfigDto(val oidc: OidcDto)

@Serializable
data class OidcDto(
    val issuer: String,
    val clientId: String,
    val scopes: List<String>,
)

/** `GET /api/me` */
@Serializable
data class MeDto(
    val username: String,
    val displayName: String,
    val roles: List<String>,
)
