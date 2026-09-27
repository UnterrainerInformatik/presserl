package info.unterrainer.presserl.admin.ui.editor

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
