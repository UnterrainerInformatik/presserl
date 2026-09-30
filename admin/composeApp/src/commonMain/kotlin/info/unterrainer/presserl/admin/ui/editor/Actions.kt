package info.unterrainer.presserl.admin.ui.editor

import info.unterrainer.presserl.admin.api.ArticleDto
import info.unterrainer.presserl.admin.api.AuthorDto

/** What the editor offers for an article, derived from the server's `allowedActions` (design D7). */
data class EditorActions(
    val editable: Boolean,
    val publish: Boolean,
    val takeOffline: Boolean,
    val delete: Boolean,
    val submit: Boolean = false,
    val withdraw: Boolean = false,
    val approve: Boolean = false,
    val reject: Boolean = false,
    val unlock: Boolean = false,
)

fun actionsFor(allowedActions: List<String>): EditorActions = EditorActions(
    editable = "EDIT" in allowedActions,
    publish = "PUBLISH" in allowedActions,
    takeOffline = "TAKE_OFFLINE" in allowedActions,
    delete = "DELETE" in allowedActions,
    submit = "SUBMIT" in allowedActions,
    withdraw = "WITHDRAW" in allowedActions,
    approve = "APPROVE" in allowedActions,
    reject = "REJECT" in allowedActions,
    unlock = "UNLOCK" in allowedActions,
)

/**
 * Who changed the article, as the editor shows it: [correcting] is the article's author while the user edits someone
 * else's article (a correction), [lastChangedBy] the author of the latest revision when that is not the article's
 * author. Users are compared by username.
 */
data class CorrectionNotice(val correcting: AuthorDto? = null, val lastChangedBy: AuthorDto? = null)

fun correctionNotice(article: ArticleDto, username: String): CorrectionNotice = CorrectionNotice(
    correcting = article.author.takeIf { it.username != username && "EDIT" in article.allowedActions },
    lastChangedBy = article.lastEditor?.takeIf { it.username != article.author.username },
)
