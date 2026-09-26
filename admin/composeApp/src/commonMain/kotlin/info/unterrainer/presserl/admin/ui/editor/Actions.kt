package info.unterrainer.presserl.admin.ui.editor

/** What the editor offers for an article, derived from the server's `allowedActions` (design D7). */
data class EditorActions(
    val editable: Boolean,
    val publish: Boolean,
    val takeOffline: Boolean,
    val delete: Boolean,
)

fun actionsFor(allowedActions: List<String>): EditorActions = EditorActions(
    editable = "EDIT" in allowedActions,
    publish = "PUBLISH" in allowedActions,
    takeOffline = "TAKE_OFFLINE" in allowedActions,
    delete = "DELETE" in allowedActions,
)
