package info.unterrainer.presserl.admin.ui

/** Display label of a newspaper-wide role from `GET /api/me`. */
fun roleLabel(role: String): String = when (role) {
    "PUBLISHER" -> "Publisher"
    "EDITOR_IN_CHIEF" -> "Editor-in-chief"
    "READER" -> "Reader"
    else -> role
}
