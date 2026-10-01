package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.RevisionSummaryDto
import info.unterrainer.presserl.admin.api.RevisionDto
import info.unterrainer.presserl.admin.api.IssueCountDto
import info.unterrainer.presserl.admin.api.ArticleCountsDto
import info.unterrainer.presserl.admin.api.AccountDto
import info.unterrainer.presserl.admin.api.AccountListDto
import info.unterrainer.presserl.admin.api.ArticleSummaryDto
import info.unterrainer.presserl.admin.api.ApiErrorDto
import info.unterrainer.presserl.admin.api.ArticleDto
import info.unterrainer.presserl.admin.api.AuthorDto
import info.unterrainer.presserl.admin.api.ClientConfigDto
import info.unterrainer.presserl.admin.api.CreatedAccountDto
import info.unterrainer.presserl.admin.api.FieldErrorDto
import info.unterrainer.presserl.admin.api.IssueDetailDto
import info.unterrainer.presserl.admin.api.IssueDto
import info.unterrainer.presserl.admin.api.IssueListDto
import info.unterrainer.presserl.admin.api.IssueRefDto
import info.unterrainer.presserl.admin.api.MeDto
import info.unterrainer.presserl.admin.api.MediaDto
import info.unterrainer.presserl.admin.api.MediaPage
import info.unterrainer.presserl.admin.api.MemberDto
import info.unterrainer.presserl.admin.api.MemberListDto
import info.unterrainer.presserl.admin.api.MySectionRoleDto
import info.unterrainer.presserl.admin.api.NewspaperDto
import info.unterrainer.presserl.admin.api.ReviewDto
import info.unterrainer.presserl.admin.api.SectionDto
import info.unterrainer.presserl.admin.api.SectionListDto
import info.unterrainer.presserl.admin.api.SectionRefDto
import info.unterrainer.presserl.admin.api.SectionRoleDto
import info.unterrainer.presserl.admin.api.TrustScopeDto
import info.unterrainer.presserl.admin.api.json
import info.unterrainer.presserl.admin.ui.account.AccountAction
import info.unterrainer.presserl.admin.ui.account.TrustSwitch
import info.unterrainer.presserl.admin.ui.account.actions
import info.unterrainer.presserl.admin.ui.account.knownTrusts
import info.unterrainer.presserl.admin.ui.account.trustSwitches
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        // older servers send no overrides
        assertEquals(emptyMap(), dto.overrides)
    }

    @Test
    fun newspaperWithOverrides() {
        val dto = json.decodeFromString<NewspaperDto>(
            """
            {
              "name": "My Newspaper",
              "subtitle": "",
              "visibility": "public",
              "settings": { "reader.text-size": "l" },
              "overrides": { "reader.text-size": "l" }
            }
            """,
        )

        assertEquals("l", dto.overrides.getValue("reader.text-size").jsonPrimitive.content)
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
    fun meWithSectionRoles() {
        val dto = json.decodeFromString<MeDto>(
            """{ "username": "nogroups", "displayName": "No Groups", "roles": [],
                 "sectionRoles": [{ "sectionId": 1, "sectionName": "Sport", "role": "SECTION_EDITOR" }] }""",
        )

        assertEquals(listOf(MySectionRoleDto(1, "Sport", "SECTION_EDITOR")), dto.sectionRoles)
    }

    @Test
    fun meWithoutAllowedActions() {
        val dto = json.decodeFromString<MeDto>("""{ "username": "papa", "displayName": "Papa", "roles": [] }""")

        assertEquals(emptyList(), dto.allowedActions)
    }

    @Test
    fun meWithAllowedActions() {
        val dto = json.decodeFromString<MeDto>(
            """{ "username": "papa", "displayName": "Papa", "roles": ["PUBLISHER"],
                 "allowedActions": ["WRITE_ARTICLES", "MANAGE_SECTIONS", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS"] }""",
        )

        assertEquals(listOf("WRITE_ARTICLES", "MANAGE_SECTIONS", "ASSIGN_SECTION_ROLES", "ADMINISTER_ACCOUNTS"), dto.allowedActions)
    }

    @Test
    fun meWithUnknownActionDecodes() {
        val dto = json.decodeFromString<MeDto>(
            """{ "username": "papa", "displayName": "Papa", "roles": [], "allowedActions": ["WRITE_ARTICLES", "REVIEW"] }""",
        )

        assertEquals(listOf("WRITE_ARTICLES", "REVIEW"), dto.allowedActions)
    }

    @Test
    fun meWithDeletionRequest() {
        val dto = json.decodeFromString<MeDto>(
            """{ "username": "papa", "displayName": "Papa", "roles": [], "deletionRequestedAt": "2026-10-02T08:15:00Z" }""",
        )

        assertEquals("2026-10-02T08:15:00Z", dto.deletionRequestedAt)
        assertEquals(null, json.decodeFromString<MeDto>("""{ "username": "papa", "displayName": "Papa", "roles": [] }""").deletionRequestedAt)
    }

    @Test
    fun deletedAuthorHasNoNames() {
        val reviews = json.decodeFromString<List<ReviewDto>>(
            """[{ "decision": "APPROVED", "level": "EDITOR_IN_CHIEF", "revision": 1,
                  "reviewer": { "username": null, "displayName": null }, "note": null, "createdAt": "2026-09-27T10:01:00Z" }]""",
        )

        assertEquals(AuthorDto(null, null), reviews[0].reviewer)
    }

    @Test
    fun accountWithDeletionRequest() {
        val dto = json.decodeFromString<AccountDto>(
            """{ "id": "r1", "username": "reader", "firstName": "", "lastName": "", "roles": ["READER"], "enabled": true,
                 "deletionRequestedAt": "2026-10-02T08:15:00Z", "allowedActions": ["LOCK", "DELETE"] }""",
        )

        assertEquals("2026-10-02T08:15:00Z", dto.deletionRequestedAt)
        assertEquals(listOf("LOCK", "DELETE"), dto.allowedActions)
    }

    @Test
    fun additiveFieldsAreIgnored() {
        val dto = json.decodeFromString<MeDto>(
            """{ "username": "papa", "displayName": "Papa", "roles": [], "scopes": ["x"] }""",
        )

        assertEquals("papa", dto.username)
    }

    @Test
    fun article() {
        val dto = json.decodeFromString<ArticleDto>(ARTICLE)

        assertEquals(42, dto.id)
        assertEquals("PUBLISHED", dto.status)
        assertEquals(AuthorDto("papa", "Papa"), dto.author)
        assertEquals(SectionRefDto(4, "Kultur", "kultur", "blue"), dto.section)
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
    fun articleWithoutPendingLevel() {
        assertNull(json.decodeFromString<ArticleDto>(ARTICLE).pendingLevel)
    }

    @Test
    fun articleWaitingForApproval() {
        val dto = json.decodeFromString<ArticleDto>(
            ARTICLE.replace("\"hasUnpublishedChanges\": true,", "\"hasUnpublishedChanges\": true, \"pendingLevel\": \"PUBLISHER\","),
        )

        assertEquals("PUBLISHER", dto.pendingLevel)
    }

    @Test
    fun articleWithoutLockIsUnlocked() {
        assertFalse(json.decodeFromString<ArticleDto>(ARTICLE).locked)
    }

    @Test
    fun lockedArticle() {
        val dto = json.decodeFromString<ArticleDto>(
            ARTICLE.replace("\"hasUnpublishedChanges\": true,", "\"hasUnpublishedChanges\": true, \"locked\": true,"),
        )

        assertTrue(dto.locked)
    }

    @Test
    fun lockedSummary() {
        val dto = json.decodeFromString<ArticleSummaryDto>(
            """{ "id": 7, "status": "OFFLINE", "author": { "username": "chief", "displayName": "Chief" },
                "section": { "id": 1, "name": "Sport", "slug": "sport", "color": "green" }, "headline": "Goal", "kicker": "",
                "revision": 1, "liveRevision": 1, "hasUnpublishedChanges": false, "pendingLevel": null, "locked": true,
                "updatedAt": "2026-09-27T10:00:00Z", "publishedAt": "2026-09-27T09:00:00Z", "allowedActions": ["UNLOCK"] }""",
        )

        assertTrue(dto.locked)
        assertEquals(listOf("UNLOCK"), dto.allowedActions)
    }

    @Test
    fun summaryWithIssue() {
        val dto = json.decodeFromString<ArticleSummaryDto>(
            """{ "id": 9, "status": "PUBLISHED", "author": { "username": "chief", "displayName": "Chief" },
                "section": { "id": 1, "name": "Sport", "slug": "sport", "color": "green" }, "issue": { "id": 4, "number": 2 },
                "headline": "Goal", "kicker": "", "revision": 1, "liveRevision": 1, "hasUnpublishedChanges": false,
                "updatedAt": "2026-09-27T10:00:00Z", "publishedAt": "2026-09-27T09:00:00Z", "allowedActions": [] }""",
        )

        assertEquals(IssueRefDto(4, 2), dto.issue)
    }

    @Test
    fun articleWithoutIssue() {
        assertNull(json.decodeFromString<ArticleDto>(ARTICLE).issue)
        assertEquals(
            IssueRefDto(4, 2),
            json.decodeFromString<ArticleDto>(ARTICLE.trimEnd().removeSuffix("}") + """, "issue": { "id": 4, "number": 2 } }""").issue,
        )
    }

    @Test
    fun issueList() {
        val dto = json.decodeFromString<IssueListDto>(
            """{ "issues": [
                { "id": 4, "number": 4, "publicationDate": "2026-10-12", "published": false, "publishedAt": null,
                  "articleCount": 2, "newest": true },
                { "id": 3, "number": 3, "publicationDate": null, "published": true, "publishedAt": "2026-10-01T08:00:00Z",
                  "articleCount": 5, "newest": false } ] }""",
        )

        assertEquals(
            listOf(
                IssueDto(4, 4, "2026-10-12", false, null, 2, true),
                IssueDto(3, 3, null, true, "2026-10-01T08:00:00Z", 5, false),
            ),
            dto.issues,
        )
    }

    @Test
    fun issueDetail() {
        val dto = json.decodeFromString<IssueDetailDto>(
            """{ "id": 4, "number": 4, "publicationDate": null, "published": false, "publishedAt": null,
                "articleCount": 0, "newest": true, "articles": [] }""",
        )

        assertEquals(4, dto.number)
        assertTrue(dto.articles.isEmpty())
    }

    @Test
    fun summaryWaitingForApproval() {
        val dto = json.decodeFromString<ArticleSummaryDto>(
            """{ "id": 7, "status": "SUBMITTED", "author": { "username": "reader", "displayName": "Reader" },
                "section": { "id": 1, "name": "Sport", "slug": "sport", "color": "green" }, "headline": "Goal", "kicker": "",
                "revision": 1, "liveRevision": null, "hasUnpublishedChanges": false, "pendingLevel": "SECTION_EDITOR",
                "updatedAt": "2026-09-27T10:00:00Z", "publishedAt": null, "allowedActions": ["APPROVE", "REJECT"] }""",
        )

        assertEquals("SECTION_EDITOR", dto.pendingLevel)
        assertEquals(listOf("APPROVE", "REJECT"), dto.allowedActions)
    }

    @Test
    fun reviews() {
        val dto = json.decodeFromString<List<ReviewDto>>(
            """[ { "decision": "REJECTED", "level": "EDITOR_IN_CHIEF", "revision": 2,
                   "reviewer": { "username": "chief", "displayName": "Chief" },
                   "note": "Too short", "createdAt": "2026-09-27T10:05:00Z" },
                 { "decision": "APPROVED", "level": "SECTION_EDITOR", "revision": 2,
                   "reviewer": { "username": "nogroups", "displayName": "No Groups" },
                   "note": null, "createdAt": "2026-09-27T10:01:00Z" } ]""",
        )

        assertEquals(
            listOf(
                ReviewDto("REJECTED", "EDITOR_IN_CHIEF", 2, AuthorDto("chief", "Chief"), "Too short", "2026-09-27T10:05:00Z"),
                ReviewDto("APPROVED", "SECTION_EDITOR", 2, AuthorDto("nogroups", "No Groups"), null, "2026-09-27T10:01:00Z"),
            ),
            dto,
        )
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

    @Test
    fun accountList() {
        val dto = json.decodeFromString<AccountListDto>(
            """
            {
              "assignableRoles": ["PUBLISHER", "EDITOR_IN_CHIEF", "READER"],
              "accounts": [
                { "id": "5f0c", "username": "chief", "firstName": "Chief", "lastName": "Editor",
                  "roles": ["EDITOR_IN_CHIEF"], "enabled": true },
                { "id": "77aa", "username": "nogroups", "firstName": "No", "lastName": "Groups",
                  "roles": [], "sectionRoles": [{ "sectionId": 1, "role": "REPORTER" }], "enabled": false,
                  "allowedActions": ["EDIT_ROLES", "RESET_PASSWORD", "UNLOCK"] }
              ]
            }
            """,
        )

        assertEquals(listOf("PUBLISHER", "EDITOR_IN_CHIEF", "READER"), dto.assignableRoles)
        assertEquals(AccountDto("5f0c", "chief", "Chief", "Editor", listOf("EDITOR_IN_CHIEF"), true), dto.accounts[0])
        assertEquals(emptyList(), dto.accounts[1].roles)
        assertEquals(listOf(SectionRoleDto(1, "REPORTER")), dto.accounts[1].sectionRoles)
        assertEquals(false, dto.accounts[1].enabled)
        assertEquals(emptyList(), dto.accounts[0].allowedActions)
        assertEquals(listOf("EDIT_ROLES", "RESET_PASSWORD", "UNLOCK"), dto.accounts[1].allowedActions)
        assertEquals(listOf(AccountAction.EDIT_ROLES, AccountAction.RESET_PASSWORD, AccountAction.UNLOCK), dto.accounts[1].actions())
        assertEquals(emptyList(), dto.accounts[0].trusts)
        assertEquals(emptyList(), dto.accounts[0].trustScopes)
    }

    @Test
    fun accountWithTrust() {
        val dto = json.decodeFromString<AccountDto>(
            """
            { "id": "5f0c", "username": "reader", "firstName": "Reader", "lastName": "", "roles": ["READER"],
              "sectionRoles": [{ "sectionId": 1, "role": "REPORTER" }], "enabled": true,
              "trusts": [{ "level": "PUBLISHER", "sectionId": null }, { "level": "SECTION_EDITOR", "sectionId": 1 }],
              "trustScopes": [{ "level": "EDITOR_IN_CHIEF", "sectionId": null }, { "level": "OMBUDSMAN", "sectionId": null }],
              "allowedActions": ["EDIT_ROLES"] }
            """,
        )

        assertEquals(listOf(TrustScopeDto("PUBLISHER", null), TrustScopeDto("SECTION_EDITOR", 1)), dto.trusts)
        assertEquals(listOf(TrustScopeDto("EDITOR_IN_CHIEF", null), TrustScopeDto("OMBUDSMAN", null)), dto.trustScopes)
        assertEquals(listOf(TrustSwitch(TrustScopeDto("EDITOR_IN_CHIEF", null), on = false)), dto.trustSwitches())
    }

    @Test
    fun trustSwitchIsOnWhenTheEntryExists() {
        val scope = TrustScopeDto("SECTION_EDITOR", 3)
        val account = AccountDto("x", "x", "", "", emptyList(), true, trusts = listOf(scope, TrustScopeDto("OMBUDSMAN")),
            trustScopes = listOf(scope))
        assertEquals(listOf(TrustSwitch(scope, on = true)), account.trustSwitches())
        assertEquals(listOf(scope), account.knownTrusts())
    }

    @Test
    fun createdAccount() {
        val dto = json.decodeFromString<CreatedAccountDto>(
            """
            {
              "account": { "id": "9a1e", "username": "lena", "firstName": "Lena", "lastName": "",
                           "roles": ["EDITOR_IN_CHIEF"], "enabled": true },
              "password": "tiger-wolke-apfel-leiter"
            }
            """,
        )

        assertEquals("lena", dto.account.username)
        assertEquals("tiger-wolke-apfel-leiter", dto.password)
        assertEquals(false, dto.toString().contains("tiger"))
    }

    @Test
    fun sectionList() {
        val dto = json.decodeFromString<SectionListDto>(
            """
            {
              "canManage": true,
              "sections": [
                { "id": 1, "name": "Sport", "slug": "sport", "color": "green", "position": 0,
                  "assignableRoles": ["SECTION_EDITOR", "REPORTER"], "canWrite": true }
              ]
            }
            """,
        )

        assertEquals(true, dto.canManage)
        assertEquals(SectionDto(1, "Sport", "sport", "green", 0, listOf("SECTION_EDITOR", "REPORTER"), canWrite = true), dto.sections.single())
    }

    @Test
    fun memberList() {
        val dto = json.decodeFromString<MemberListDto>(
            """
            {
              "assignableRoles": ["SECTION_EDITOR", "REPORTER"],
              "members": [
                { "accountId": "5f0c", "username": "nogroups", "firstName": "No", "lastName": "Groups",
                  "role": "SECTION_EDITOR" }
              ]
            }
            """,
        )

        assertEquals(MemberDto("5f0c", "nogroups", "No", "Groups", "SECTION_EDITOR"), dto.members.single())
    }

    @Test
    fun accountRefusal() {
        val dto = json.decodeFromString<ApiErrorDto>("""{ "errors": [{ "field": "roles", "message": "you may not assign [PUBLISHER]" }] }""")

        assertEquals(FieldErrorDto("roles", "you may not assign [PUBLISHER]"), dto.errors.single())
    }

    @Test
    fun mediaCarriesItsVersion() {
        val media = json.decodeFromString<MediaDto>("""{ "id": 17, "version": 2, "contentType": "image/png", "width": 800,
            "height": 600, "size": 5000, "uploadedBy": { "username": "anna", "displayName": "Anna" },
            "uploadedAt": "2026-09-27T14:03:11.402Z", "renditions": {} }""")
        val page = json.decodeFromString<MediaPage>("""{ "items": [{ "id": 17, "version": 2, "contentType": "image/png",
            "width": 800, "height": 600, "size": 5000, "uploadedBy": { "username": "anna", "displayName": "Anna" },
            "uploadedAt": "2026-09-27T14:03:11.402Z", "renditions": {}, "usageCount": 0 }], "next": null }""")

        assertEquals(2, media.version)
        assertEquals(2, page.items.single().version)
        assertNull(page.next)
    }

    @Test
    fun articleWithLastEditor() {
        val dto = json.decodeFromString<ArticleDto>(
            ARTICLE.replace("\"section\":", "\"lastEditor\": { \"username\": \"chief\", \"displayName\": \"Lena\" }, \"section\":"),
        )

        assertEquals(AuthorDto("chief", "Lena"), dto.lastEditor)
        assertNull(json.decodeFromString<ArticleDto>(ARTICLE).lastEditor)
    }

    @Test
    fun summaryWithCreatedAt() {
        val dto = json.decodeFromString<ArticleSummaryDto>(
            """{ "id": 9, "status": "DRAFT", "author": { "username": "chief", "displayName": "Chief" }, "headline": "Goal",
                "kicker": "", "revision": 1, "hasUnpublishedChanges": false, "createdAt": "2026-09-30T10:00:00Z",
                "updatedAt": "2026-09-30T11:00:00Z", "allowedActions": [] }""",
        )

        assertEquals("2026-09-30T10:00:00Z", dto.createdAt)
    }

    @Test
    fun revisionsWithAuthor() {
        val summary = json.decodeFromString<RevisionSummaryDto>(
            """{ "number": 2, "headline": "H", "author": { "username": "chief", "displayName": "Lena" }, "createdAt": "t",
                "updatedAt": "t", "publishedAt": null, "live": false }""",
        )
        val revision = json.decodeFromString<RevisionDto>(
            """{ "number": 2, "headline": "H", "author": { "username": "chief", "displayName": "Lena" }, "createdAt": "t",
                "updatedAt": "t", "live": false, "kicker": "", "subheadline": "", "lead": "", "body": { "version": 1, "blocks": [] } }""",
        )

        assertEquals(AuthorDto("chief", "Lena"), summary.author)
        assertEquals(AuthorDto("chief", "Lena"), revision.author)
    }

    @Test
    fun sectionWithArticleCounts() {
        val dto = json.decodeFromString<SectionDto>(
            """{ "id": 1, "name": "Sport", "slug": "sport", "color": "green", "position": 0, "assignableRoles": [],
                "canWrite": true, "articleCounts": { "live": 2, "total": 4,
                "issues": [{ "issueId": 12, "number": 2, "count": 2 }, { "issueId": 11, "number": 1, "count": 1 }] } }""",
        )

        assertEquals(ArticleCountsDto(2, 4, listOf(IssueCountDto(12, 2, 2), IssueCountDto(11, 1, 1))), dto.articleCounts)
        assertNull(json.decodeFromString<SectionDto>(
            """{ "id": 1, "name": "Sport", "slug": "sport", "color": "green", "position": 0, "assignableRoles": [],
                "articleCounts": null }""",
        ).articleCounts)
    }

    @Test
    fun sectionlessReporterInMeAndAccounts() {
        val me = json.decodeFromString<MeDto>(
            """{ "username": "pia", "displayName": "Pia", "roles": [], "sectionRoles": [], "sectionlessReporter": true,
                "allowedActions": ["USE_MEDIA"] }""",
        )
        val list = json.decodeFromString<AccountListDto>(
            """{ "assignableRoles": ["EDITOR_IN_CHIEF", "READER"], "mayAssignSectionlessReporter": true, "accounts": [
                { "id": "p", "username": "pia", "firstName": "Pia", "lastName": "", "roles": [], "sectionRoles": [],
                  "sectionlessReporter": true, "enabled": true } ] }""",
        )

        assertTrue(me.sectionlessReporter)
        assertTrue(list.mayAssignSectionlessReporter)
        assertTrue(list.accounts.single().sectionlessReporter)
        assertFalse(json.decodeFromString<MeDto>("""{ "username": "u", "displayName": "U", "roles": [] }""").sectionlessReporter)
    }
}
