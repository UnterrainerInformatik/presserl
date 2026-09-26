## 1. Backend — data model

- [x] 1.1 Flyway `V2__articles.sql` with `article` and `article_revision` per design D1 (check constraint incl. reserved `SUBMITTED`, indexes, cascade)
- [x] 1.2 Panache entities `ArticleEntity` (`@Version`) and `ArticleRevisionEntity` (composite key), `ArticleStatus` enum
- [x] 1.3 Dev realm: users `chief` (`editor-in-chief`) and `reader` (`reader`), password = username; confirm `RealmTemplateDriftTest` still passes

## 2. Backend — content validation

- [x] 2.1 Limits constants and `FieldError`; validator for the four text fields (trim, length, no control characters)
- [x] 2.2 `ArticleBodyValidator` for format v1 per design D3 (block types, required/unknown fields, runs, `\n` only in runs, 500 blocks, 200 000 characters, `version` = 1), collecting all errors with paths
- [x] 2.3 Unit tests: each block type valid, unknown type/field/mark, missing fields, empty run text, empty list, wrong version, control characters, block and size limits, multiple errors reported together, markup kept verbatim

## 3. Backend — service and policy

- [x] 3.1 `SecurityIdentityAugmentor` adding newspaper roles from the `groups` claim (reusing `NewspaperRole.fromGroups`)
- [x] 3.2 `ArticlePolicy` computing `allowedActions` per design D4 (role → ownership → state; `403` vs `409` distinction)
- [x] 3.3 `ArticleService`: create, save with working-revision rule and explicit version check (D2), delete (never-published only), publish (headline required), take offline, list with filters and latest revision in one query (D6), revisions list/get
- [x] 3.4 Unit tests for `ArticlePolicy` covering every row of the D4 table and each status

## 4. Backend — REST

- [x] 4.1 DTOs (`ArticleDto`, `ArticleSummaryDto`, `ArticleContent`, `AuthorDto`, `RevisionSummaryDto`, `RevisionDto`, `ApiErrorDto`) per design D5; strict unknown-field handling for `ArticleContent`
- [x] 4.2 `ArticleResource` for all endpoints of D5 with `@RolesAllowed({"PUBLISHER","EDITOR_IN_CHIEF"})`, `201` + `Location` on create
- [x] 4.3 Exception mappers: validation → `400 {errors}`, forbidden → `403`, not found → `404`, state conflict and optimistic lock → `409`, all with the `errors` body
- [x] 4.4 `@QuarkusTest` for every scenario in `specs/articles/spec.md`: roles (publisher, chief, reader, no token), content defaults and limits, body round-trip, working revision / new revision after publish / republish, stale version `409`, edit/delete ownership, delete after publish `409`, publish rules incl. chief `403` and empty headline, offline rules, `allowedActions` examples, list filters and unknown status, `404`s, revision history

## 5. Admin — API client

- [x] 5.1 DTOs in `Dtos.kt` per design D8 (`body` as `JsonObject`, timestamps as strings)
- [x] 5.2 `ApiClient` methods for list, get, create, update, delete, publish, offline, revisions, revision
- [x] 5.3 Kotlin tests: deserialisation of the D5 `ArticleDto` example and an error body; `ApiClientTest` cases for request paths, methods, query parameters and bodies (MockEngine)

## 6. Contract / Docs

- [x] 6.1 `ai/primer/endpoints.md`: all article endpoints with auth, params, bodies, examples, errors, side effects; body format v1 and `allowedActions` semantics
- [x] 6.2 `http/articles.http`: create, save, stale save `409`, publish, edit after publish, republish, offline, revisions, delete draft, reader `403` (token for `reader`), invalid body `400`; env entries for the extra users
- [x] 6.3 `docs/architecture.md`: REST sketch (delete, revisions, offline naming), data model entries for Article/ArticleRevision (working revision, live revision by number, body format v1)
- [x] 6.4 `ai/open-proposals.md`: M1 entry reduced to `article-editor` and `reader-articles`; M3 entry notes `allowedActions` already exists and only its rules grow

## 7. Verification

- [x] 7.1 `cd backend && ./mvnw verify` green
- [x] 7.2 `cd admin && ./gradlew check` green
- [x] 7.3 Run `http/*.http` against `quarkus:dev` — all assertions pass
- [x] 7.4 `openspec validate articles-core --strict`
