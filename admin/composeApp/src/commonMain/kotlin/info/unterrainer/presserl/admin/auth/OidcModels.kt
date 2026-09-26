package info.unterrainer.presserl.admin.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The parts of `.well-known/openid-configuration` the app needs. */
@Serializable
data class OidcDiscovery(
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("end_session_endpoint") val endSessionEndpoint: String? = null,
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("id_token") val idToken: String? = null,
)
