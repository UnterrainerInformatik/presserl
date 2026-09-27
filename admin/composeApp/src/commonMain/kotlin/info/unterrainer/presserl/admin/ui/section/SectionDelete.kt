package info.unterrainer.presserl.admin.ui.section

import info.unterrainer.presserl.admin.ui.describe
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException

/** What the section list shows after "Delete"; the list is reloaded in every case. */
sealed interface SectionDeleteResult {
    data object Deleted : SectionDeleteResult

    /** `409`: articles still belong to the section; shown as the localized `section_not_empty`. */
    data object NotEmpty : SectionDeleteResult

    data class Failed(val message: String) : SectionDeleteResult
}

/** Runs [delete] (`DELETE /api/sections/{id}`) and maps its outcome; cancellation passes through. */
suspend fun deleteSection(delete: suspend () -> Unit): SectionDeleteResult = try {
    delete()
    SectionDeleteResult.Deleted
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    // Browser fetch failures surface as kotlin.Error, not Exception
    if (e is ResponseException && e.response.status == HttpStatusCode.Conflict) {
        SectionDeleteResult.NotEmpty
    } else {
        SectionDeleteResult.Failed(describe(e))
    }
}
