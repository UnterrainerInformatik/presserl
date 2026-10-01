package info.unterrainer.presserl.admin.api

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * `GET /api/newspaper` and `PUT /api/newspaper/settings`; [overrides] holds the entries of [settings] that come from a
 * newspaper override (absent on older servers).
 */
@Serializable
data class NewspaperDto(
    val name: String,
    val subtitle: String,
    val visibility: String,
    val settings: JsonObject,
    val overrides: JsonObject = JsonObject(emptyMap()),
)

/** `GET /api/client-config`; [spellCheck] is `false` on older servers that do not send it. */
@Serializable
data class ClientConfigDto(val oidc: OidcDto, val spellCheck: Boolean = false)

/** Body of `POST /api/spell-check`. */
@Serializable
data class SpellCheckRequestDto(val text: String)

/** Response of `POST /api/spell-check`: the findings in text order. */
@Serializable
data class SpellCheckResponseDto(val matches: List<SpellMatchDto>)

/** One finding; [offset] and [length] are UTF-16 code units of the checked text, like Kotlin string indices. */
@Serializable
data class SpellMatchDto(
    val offset: Int,
    val length: Int,
    val message: String,
    val replacements: List<String> = emptyList(),
)

@Serializable
data class OidcDto(
    val issuer: String,
    val clientId: String,
    val scopes: List<String>,
)

/**
 * `GET /api/me`; [sectionRoles] are ordered by section position, [sectionlessReporter] is the marker that lets the
 * account use the images without writing articles, [allowedActions] are the newspaper-wide actions the server grants
 * (unknown values are kept and ignored by the app), [deletionRequestedAt] is the ISO-8601 instant of the user's pending
 * deletion request (`null` for none).
 */
@Serializable
data class MeDto(
    val username: String,
    val displayName: String,
    val roles: List<String>,
    val sectionRoles: List<MySectionRoleDto> = emptyList(),
    val sectionlessReporter: Boolean = false,
    val allowedActions: List<String> = emptyList(),
    val deletionRequestedAt: String? = null,
)

/** `POST`/`DELETE /api/me/deletion-request`: the pending request's ISO-8601 instant, `null` for none. */
@Serializable
data class DeletionRequestDto(val deletionRequestedAt: String? = null)

/** A section role of the logged-in user, with the section's name. */
@Serializable
data class MySectionRoleDto(
    val sectionId: Long,
    val sectionName: String,
    val role: String,
)

/**
 * Name snapshot of an article's author, a revision's author, a reviewer or an uploader; both `null` once that account
 * was deleted (shown as "former newsroom member", see `authorLabel`).
 */
@Serializable
data class AuthorDto(
    val username: String? = null,
    val displayName: String? = null,
)

/**
 * `POST /api/media` and `GET /api/media/{id}`: an uploaded image as stored after re-encoding. [contentType] is
 * `image/jpeg` or `image/png`, [size] the stored file's bytes; [uploadedAt] is an ISO-8601 string. [renditions] maps
 * `thumbnail`, `web` and `print` to their sizes; empty while the server has not produced them yet. [version] is `0`
 * after the upload and incremented by every edit (`POST /api/media/{id}/edit`). [description] (`null` for none) and
 * [tags] (sorted case-insensitively) are set with `PUT /api/media/{id}/details` and do not change the version.
 */
@Serializable
data class MediaDto(
    val id: Long,
    val contentType: String,
    val width: Int,
    val height: Int,
    val size: Long,
    val uploadedBy: AuthorDto,
    val uploadedAt: String,
    val renditions: Map<String, RenditionDto> = emptyMap(),
    val version: Long = 0,
    val description: String? = null,
    val tags: List<String> = emptyList(),
)

/** Entry of `GET /api/media`: a [MediaDto]'s fields plus [usageCount], the number of articles using the image. */
@Serializable
data class MediaListItemDto(
    val id: Long,
    val version: Long = 0,
    val contentType: String,
    val width: Int,
    val height: Int,
    val size: Long,
    val uploadedBy: AuthorDto,
    val uploadedAt: String,
    val renditions: Map<String, RenditionDto> = emptyMap(),
    val usageCount: Long = 0,
    val description: String? = null,
    val tags: List<String> = emptyList(),
)

/** A freshly uploaded or updated [media] as a grid entry with [usageCount]. */
fun MediaDto.asListItem(usageCount: Long = 0): MediaListItemDto =
    MediaListItemDto(id, version, contentType, width, height, size, uploadedBy, uploadedAt, renditions, usageCount, description, tags)

/**
 * Filters of `GET /api/media`, combined with AND: every tag in [tags], every word of [q] in description or tags,
 * only unused media ([unused]) and only the user's own uploads ([mine]).
 */
data class MediaFilter(
    val tags: List<String> = emptyList(),
    val q: String = "",
    val unused: Boolean = false,
    val mine: Boolean = false,
) {
    /** Whether any filter is active. */
    val active: Boolean get() = tags.isNotEmpty() || q.isNotBlank() || unused || mine
}

/** Body of `PUT /api/media/{id}/details`: both fields are always sent (`null` description for none). */
@Serializable
data class MediaDetailsRequest(val description: String?, val tags: List<String>)

/** A tag of `GET /api/media/tags` in its most frequent spelling and how many media carry it. */
@Serializable
data class MediaTagDto(val name: String, val count: Long)

/** `GET /api/media/tags`: tags, most used first. */
@Serializable
data class MediaTagList(val items: List<MediaTagDto> = emptyList())

/** One page of `GET /api/media`, newest first; [next] is the `before` value of the next page, `null` on the last. */
@Serializable
data class MediaPage(val items: List<MediaListItemDto>, val next: Long? = null)

/**
 * `GET /api/media/{id}/usage`: whether the user may edit the image and the articles using it, most recently
 * changed first.
 */
@Serializable
data class MediaUsageDto(val mayEdit: Boolean, val articles: List<MediaUseDto> = emptyList())

/**
 * An article using a media as lead image; [headline] is that of its latest revision. [live]: the live revision uses
 * the image; [latest]: the latest (working) revision uses it; [older]: only older revisions use it.
 */
@Serializable
data class MediaUseDto(
    val id: Long,
    val headline: String,
    val section: SectionRefDto? = null,
    val author: AuthorDto,
    val status: String,
    val pendingLevel: String? = null,
    val publishedAt: String? = null,
    val updatedAt: String,
    val live: Boolean = false,
    val latest: Boolean = false,
    val older: Boolean = false,
)

/** A crop rectangle in pixels of the stored image. */
@Serializable
data class CropRequest(val x: Int, val y: Int, val width: Int, val height: Int)

/** An ellipse to pixelate, centre and radii in pixels of the stored image. */
@Serializable
data class EllipseRequest(val cx: Int, val cy: Int, val rx: Int, val ry: Int)

/**
 * Body of `POST /api/media/{id}/edit`: the [version] the edit is based on (`409` if stale), an optional [crop] and the
 * ellipses to [pixelate]; at least one of both.
 */
@Serializable
data class EditMediaRequest(val version: Long, val crop: CropRequest? = null, val pixelate: List<EllipseRequest> = emptyList())

/** Size of one rendition of a [MediaDto]. */
@Serializable
data class RenditionDto(val width: Int, val height: Int, val size: Long)

/** The lead image of a revision as the server returns it; [width] and [height] are those of the stored image. */
@Serializable
data class LeadImageDto(val mediaId: Long, val caption: String, val width: Int, val height: Int)

/** The lead image in an article request: an uploaded media and its caption (plain text, at most 300 characters). */
@Serializable
data class LeadImageRequest(val mediaId: Long, val caption: String)

/** The section an article belongs to; [color] is a palette key. */
@Serializable
data class SectionRefDto(
    val id: Long,
    val name: String,
    val slug: String,
    val color: String,
)

/** The issue an article belongs to. */
@Serializable
data class IssueRefDto(
    val id: Long,
    val number: Int,
)

/**
 * `GET/POST/PUT /api/articles/{id}` and the article actions (publish, submit, approve, reject,
 * withdraw, offline, unlock). [section] is `null` only for an article the server has not filed under a
 * section yet, [issue] `null` for an article without issue. Content fields are those of the latest revision ([revision]); [body] is format v1
 * (`{"version": 1, "blocks": [...]}`). [pendingLevel] is the approval level the article waits for
 * (`SECTION_EDITOR`, `EDITOR_IN_CHIEF`, `PUBLISHER`), `null` while no submission is pending.
 * [locked] is the emergency-brake lock: a publisher took the article offline, only a publisher puts it back online.
 * [lastEditor] wrote the latest revision (the author unless someone corrected it; `null` on older servers).
 * [readerVisible] tells whether readers see the article (published in a published issue); [frontPageWeight] is the
 * front-page weight (1–999, lower comes first), `null` for none.
 * Timestamps are ISO-8601 strings.
 */
@Serializable
data class ArticleDto(
    val id: Long,
    val status: String,
    val author: AuthorDto,
    val lastEditor: AuthorDto? = null,
    val section: SectionRefDto? = null,
    val issue: IssueRefDto? = null,
    val readerVisible: Boolean = false,
    val frontPageWeight: Int? = null,
    val revision: Int,
    val liveRevision: Int? = null,
    val hasUnpublishedChanges: Boolean,
    val pendingLevel: String? = null,
    val locked: Boolean = false,
    val version: Long,
    val createdAt: String,
    val updatedAt: String,
    val publishedAt: String? = null,
    val kicker: String,
    val headline: String,
    val subheadline: String,
    val lead: String,
    val body: JsonObject,
    val leadImage: LeadImageDto? = null,
    val allowedActions: List<String>,
)

/**
 * Entry of `GET /api/articles` and of the articles of an issue; [issue] is `null` for an article without issue.
 * [readerVisible] and [frontPageWeight] as in [ArticleDto].
 */
@Serializable
data class ArticleSummaryDto(
    val id: Long,
    val status: String,
    val author: AuthorDto,
    val section: SectionRefDto? = null,
    val issue: IssueRefDto? = null,
    val readerVisible: Boolean = false,
    val frontPageWeight: Int? = null,
    val headline: String,
    val kicker: String,
    val revision: Int,
    val liveRevision: Int? = null,
    val hasUnpublishedChanges: Boolean,
    val pendingLevel: String? = null,
    val locked: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String,
    val publishedAt: String? = null,
    val allowedActions: List<String>,
)

/**
 * Request body of `POST /api/articles` and, with a version, `PUT /api/articles/{id}`. A missing [body] means an
 * empty document; a missing [sectionId] lets the server choose the section (create) or keeps it (save).
 * [leadImage] is always sent, `null` included: the server replaces the whole content, so leaving it out would
 * remove the image.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ArticleContent(
    val kicker: String = "",
    val headline: String = "",
    val subheadline: String = "",
    val lead: String = "",
    val body: JsonObject? = null,
    val sectionId: Long? = null,
    @EncodeDefault val leadImage: LeadImageRequest? = null,
)

/** Entry of `GET /api/articles/{id}/revisions`; [author] wrote the revision (`null` on older servers). */
@Serializable
data class RevisionSummaryDto(
    val number: Int,
    val headline: String,
    val author: AuthorDto? = null,
    val createdAt: String,
    val updatedAt: String,
    val publishedAt: String? = null,
    val live: Boolean,
)

/** `GET /api/articles/{id}/revisions/{number}`; [author] wrote the revision (`null` on older servers). */
@Serializable
data class RevisionDto(
    val number: Int,
    val headline: String,
    val author: AuthorDto? = null,
    val createdAt: String,
    val updatedAt: String,
    val publishedAt: String? = null,
    val live: Boolean,
    val kicker: String,
    val subheadline: String,
    val lead: String,
    val body: JsonObject,
    val leadImage: LeadImageDto? = null,
)

/**
 * Entry of `GET /api/articles/{id}/reviews`, newest first: [decision] `APPROVED` or `REJECTED`, [level] the level
 * the article waited for, [note] set for rejections only.
 */
@Serializable
data class ReviewDto(
    val decision: String,
    val level: String,
    val revision: Int,
    val reviewer: AuthorDto,
    val note: String? = null,
    val createdAt: String,
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
 * [sectionRoles] are ordered by section position, [sectionlessReporter] is the marker "Redakteur (ohne Ressort)",
 * [trusts] the levels that trust the account, [trustScopes] the trust
 * entries the user may set or clear on it (both ordered publisher, editor-in-chief, section editor by section
 * position), [allowedActions] what the user may do with it now (`EDIT_ROLES`, `RESET_PASSWORD`, `LOCK`, `UNLOCK`,
 * `DELETE`), [deletionRequestedAt] the ISO-8601 instant of the account's pending deletion request (`null` for none).
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
    val sectionlessReporter: Boolean = false,
    val trusts: List<TrustScopeDto> = emptyList(),
    val trustScopes: List<TrustScopeDto> = emptyList(),
    val allowedActions: List<String> = emptyList(),
    val deletionRequestedAt: String? = null,
)

/**
 * A trust entry: the approval [level] (`SECTION_EDITOR`, `EDITOR_IN_CHIEF`, `PUBLISHER`) and, for `SECTION_EDITOR`
 * only, the section; [sectionId] is `null` for the newspaper-wide levels.
 */
@Serializable
data class TrustScopeDto(
    val level: String,
    val sectionId: Long? = null,
)

/** Request body of `PUT /api/accounts/{id}/trust`; [sectionId] is always sent, `null` for newspaper-wide levels. */
@Serializable
data class SetTrustRequest(
    val level: String,
    val sectionId: Long?,
    val trusted: Boolean,
)

/** A section role of an account (`SECTION_EDITOR` or `REPORTER`), in account lists and `POST /api/accounts`. */
@Serializable
data class SectionRoleDto(
    val sectionId: Long,
    val role: String,
)

/**
 * `GET /api/accounts`: all accounts, the roles the requesting user may assign and whether they may assign the
 * sectionless-reporter marker.
 */
@Serializable
data class AccountListDto(
    val assignableRoles: List<String>,
    val accounts: List<AccountDto>,
    val mayAssignSectionlessReporter: Boolean = false,
)

/** `GET /api/accounts/username-suggestion` */
@Serializable
data class UsernameSuggestionDto(val username: String)

/**
 * Request body of `POST /api/accounts`; [roles] may be empty when [sectionRoles] is not or [sectionlessReporter] is
 * `true`. [sectionlessReporter] is left out when `null` (the user may not assign it).
 */
@Serializable
data class CreateAccountRequest(
    val firstName: String,
    val lastName: String,
    val username: String,
    val roles: List<String>,
    val sectionRoles: List<SectionRoleDto> = emptyList(),
    val sectionlessReporter: Boolean? = null,
)

/**
 * Request body of `PUT /api/accounts/{id}/roles`: the complete roles; both lists are always sent, [sectionlessReporter]
 * only when the user may change it (left out when `null`: the server keeps the marker, or sets it when the last
 * section role goes).
 */
@Serializable
data class EditRolesRequest(
    val roles: List<String>,
    val sectionRoles: List<SectionRoleDto>,
    val sectionlessReporter: Boolean? = null,
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
 * [canWrite] whether the user may write articles in it, [articleCounts] how many articles it holds (writers only,
 * `null` otherwise).
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
    val articleCounts: ArticleCountsDto? = null,
)

/**
 * The articles of a section now: [live] published, [total] in any status, and per issue holding at least one of them
 * ([issues], highest issue number first).
 */
@Serializable
data class ArticleCountsDto(
    val live: Int,
    val total: Int,
    val issues: List<IssueCountDto> = emptyList(),
)

/** The number of a section's articles in one issue. */
@Serializable
data class IssueCountDto(
    val issueId: Long,
    val number: Int,
    val count: Int,
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

/**
 * Entry of `GET /api/issues`: [publicationDate] is an ISO date (`2026-10-12`) or `null`, [publishedAt] the time of the
 * latest switch to published (`null` while not published), [articleCount] counts articles of any status, [newest]
 * marks the issue with the highest number, which collects newly published articles.
 */
@Serializable
data class IssueDto(
    val id: Long,
    val number: Int,
    val publicationDate: String? = null,
    val published: Boolean,
    val publishedAt: String? = null,
    val articleCount: Int,
    val newest: Boolean,
)

/** `GET /api/issues`: highest number first. */
@Serializable
data class IssueListDto(val issues: List<IssueDto>)

/**
 * `GET/PUT /api/issues/{id}`, `POST /api/issues` and the issue actions: the [IssueDto] fields and the [articles] in
 * issue order; the first one is the lead story.
 */
@Serializable
data class IssueDetailDto(
    val id: Long,
    val number: Int,
    val publicationDate: String? = null,
    val published: Boolean,
    val publishedAt: String? = null,
    val articleCount: Int,
    val newest: Boolean,
    val articles: List<ArticleSummaryDto> = emptyList(),
)

/** Request body of `PUT /api/articles/{id}/front-page-weight`; [weight] is always sent, `null` clears it. */
@Serializable
data class FrontPageWeightRequest(val weight: Int?)

/** Request body of `POST /api/issues` and `PUT /api/issues/{id}`; [publicationDate] is always sent, `null` clears it. */
@Serializable
data class IssueDateRequest(val publicationDate: String?)
