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

/** Author snapshot of an article. */
@Serializable
data class AuthorDto(
    val username: String,
    val displayName: String,
)

/**
 * `GET/POST/PUT /api/articles/{id}`, publish and offline. Content fields are those of the latest
 * revision ([revision]); [body] is format v1 (`{"version": 1, "blocks": [...]}`).
 * Timestamps are ISO-8601 strings.
 */
@Serializable
data class ArticleDto(
    val id: Long,
    val status: String,
    val author: AuthorDto,
    val revision: Int,
    val liveRevision: Int? = null,
    val hasUnpublishedChanges: Boolean,
    val version: Long,
    val createdAt: String,
    val updatedAt: String,
    val publishedAt: String? = null,
    val kicker: String,
    val headline: String,
    val subheadline: String,
    val lead: String,
    val body: JsonObject,
    val allowedActions: List<String>,
)

/** Entry of `GET /api/articles`. */
@Serializable
data class ArticleSummaryDto(
    val id: Long,
    val status: String,
    val author: AuthorDto,
    val headline: String,
    val kicker: String,
    val revision: Int,
    val liveRevision: Int? = null,
    val hasUnpublishedChanges: Boolean,
    val updatedAt: String,
    val publishedAt: String? = null,
    val allowedActions: List<String>,
)

/** Request body of `POST /api/articles` and, with a version, `PUT /api/articles/{id}`. A missing [body] means an empty document. */
@Serializable
data class ArticleContent(
    val kicker: String = "",
    val headline: String = "",
    val subheadline: String = "",
    val lead: String = "",
    val body: JsonObject? = null,
)

/** Entry of `GET /api/articles/{id}/revisions`. */
@Serializable
data class RevisionSummaryDto(
    val number: Int,
    val headline: String,
    val createdAt: String,
    val updatedAt: String,
    val publishedAt: String? = null,
    val live: Boolean,
)

/** `GET /api/articles/{id}/revisions/{number}` */
@Serializable
data class RevisionDto(
    val number: Int,
    val headline: String,
    val createdAt: String,
    val updatedAt: String,
    val publishedAt: String? = null,
    val live: Boolean,
    val kicker: String,
    val subheadline: String,
    val lead: String,
    val body: JsonObject,
)

/** Error body of refused API requests (`400`, `403`, `404`, `409`, `503`). */
@Serializable
data class ApiErrorDto(val errors: List<FieldErrorDto>)

/** [field] is a path such as `body.blocks[2].content[0].text`, or `null` if the error concerns no field. */
@Serializable
data class FieldErrorDto(
    val field: String? = null,
    val message: String,
)

/** Entry of `GET /api/accounts`; [roles] are newspaper roles in the order publisher, editor-in-chief, reader. */
@Serializable
data class AccountDto(
    val id: String,
    val username: String,
    val firstName: String,
    val lastName: String,
    val roles: List<String>,
    val enabled: Boolean,
)

/** `GET /api/accounts`: all accounts and the roles the requesting user may assign. */
@Serializable
data class AccountListDto(
    val assignableRoles: List<String>,
    val accounts: List<AccountDto>,
)

/** `GET /api/accounts/username-suggestion` */
@Serializable
data class UsernameSuggestionDto(val username: String)

/** Request body of `POST /api/accounts`. */
@Serializable
data class CreateAccountRequest(
    val firstName: String,
    val lastName: String,
    val username: String,
    val roles: List<String>,
)

/** Response of `POST /api/accounts`; [password] exists only in this response. */
@Serializable
data class CreatedAccountDto(
    val account: AccountDto,
    val password: String,
) {
    override fun toString(): String = "CreatedAccountDto(account=$account, password=***)"
}
