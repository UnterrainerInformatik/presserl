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

/** `GET /api/me`; [sectionRoles] are ordered by section position. */
@Serializable
data class MeDto(
    val username: String,
    val displayName: String,
    val roles: List<String>,
    val sectionRoles: List<MySectionRoleDto> = emptyList(),
)

/** A section role of the logged-in user, with the section's name. */
@Serializable
data class MySectionRoleDto(
    val sectionId: Long,
    val sectionName: String,
    val role: String,
)

/** Author snapshot of an article. */
@Serializable
data class AuthorDto(
    val username: String,
    val displayName: String,
)

/** The section an article belongs to; [color] is a palette key. */
@Serializable
data class SectionRefDto(
    val id: Long,
    val name: String,
    val slug: String,
    val color: String,
)

/**
 * `GET/POST/PUT /api/articles/{id}`, publish and offline. [section] is `null` only for an article
 * the server has not filed under a section yet. Content fields are those of the latest
 * revision ([revision]); [body] is format v1 (`{"version": 1, "blocks": [...]}`).
 * Timestamps are ISO-8601 strings.
 */
@Serializable
data class ArticleDto(
    val id: Long,
    val status: String,
    val author: AuthorDto,
    val section: SectionRefDto? = null,
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
    val section: SectionRefDto? = null,
    val headline: String,
    val kicker: String,
    val revision: Int,
    val liveRevision: Int? = null,
    val hasUnpublishedChanges: Boolean,
    val updatedAt: String,
    val publishedAt: String? = null,
    val allowedActions: List<String>,
)

/**
 * Request body of `POST /api/articles` and, with a version, `PUT /api/articles/{id}`. A missing [body] means an
 * empty document; a missing [sectionId] lets the server choose the section (create) or keeps it (save).
 */
@Serializable
data class ArticleContent(
    val kicker: String = "",
    val headline: String = "",
    val subheadline: String = "",
    val lead: String = "",
    val body: JsonObject? = null,
    val sectionId: Long? = null,
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

/**
 * Entry of `GET /api/accounts`; [roles] are newspaper roles in the order publisher, editor-in-chief, reader,
 * [sectionRoles] are ordered by section position, [allowedActions] what the user may do with it now
 * (`RESET_PASSWORD`, `LOCK`, `UNLOCK`).
 */
@Serializable
data class AccountDto(
    val id: String,
    val username: String,
    val firstName: String,
    val lastName: String,
    val roles: List<String>,
    val enabled: Boolean,
    val sectionRoles: List<SectionRoleDto> = emptyList(),
    val allowedActions: List<String> = emptyList(),
)

/** A section role of an account (`SECTION_EDITOR` or `REPORTER`), in account lists and `POST /api/accounts`. */
@Serializable
data class SectionRoleDto(
    val sectionId: Long,
    val role: String,
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

/** Request body of `POST /api/accounts`; [roles] may be empty when [sectionRoles] is not. */
@Serializable
data class CreateAccountRequest(
    val firstName: String,
    val lastName: String,
    val username: String,
    val roles: List<String>,
    val sectionRoles: List<SectionRoleDto> = emptyList(),
)

/** Response of `POST /api/accounts`; [password] exists only in this response. */
@Serializable
data class CreatedAccountDto(
    val account: AccountDto,
    val password: String,
) {
    override fun toString(): String = "CreatedAccountDto(account=$account, password=***)"
}

/**
 * One section; [color] is a palette key (`red` … `pink`), [assignableRoles] the section roles the user may assign in it,
 * [canWrite] whether the user may write articles in it.
 */
@Serializable
data class SectionDto(
    val id: Long,
    val name: String,
    val slug: String,
    val color: String,
    val position: Int,
    val assignableRoles: List<String>,
    val canWrite: Boolean = false,
)

/** `GET /api/sections` and `PUT /api/sections/order`: [canManage] allows creating, changing and reordering sections. */
@Serializable
data class SectionListDto(
    val canManage: Boolean,
    val sections: List<SectionDto>,
)

/** Request body of `POST /api/sections` (without [color] the server picks one) and `PUT /api/sections/{id}`. */
@Serializable
data class SectionRequest(
    val name: String,
    val color: String? = null,
)

/** A member of a section with their section role. */
@Serializable
data class MemberDto(
    val accountId: String,
    val username: String,
    val firstName: String,
    val lastName: String,
    val role: String,
)

/** `GET /api/sections/{id}/members`: section editors first, then by username. */
@Serializable
data class MemberListDto(
    val assignableRoles: List<String>,
    val members: List<MemberDto>,
)
