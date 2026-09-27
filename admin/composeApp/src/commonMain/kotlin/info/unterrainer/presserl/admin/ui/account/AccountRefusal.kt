package info.unterrainer.presserl.admin.ui.account

import info.unterrainer.presserl.admin.ui.apiErrorsOf
import info.unterrainer.presserl.admin.ui.describe

/**
 * Field errors of a refused account request (first message per field); anything not about an [AccountField] becomes
 * the general message.
 */
internal suspend fun accountRefusal(e: Throwable): Pair<Map<AccountField, String>, String?> {
    val errors = apiErrorsOf(e) ?: return emptyMap<AccountField, String>() to describe(e)
    val fields = mutableMapOf<AccountField, String>()
    val general = mutableListOf<String>()
    errors.forEach { error ->
        val field = AccountField.entries.firstOrNull { it.wire == error.field }
        if (field != null) fields.getOrPut(field) { error.message } else general += error.message
    }
    return fields to general.joinToString(" ").ifEmpty { null }
}
