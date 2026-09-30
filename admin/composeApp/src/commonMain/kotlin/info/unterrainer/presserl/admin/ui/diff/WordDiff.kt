package info.unterrainer.presserl.admin.ui.diff

import info.unterrainer.presserl.admin.api.RevisionDto
import info.unterrainer.presserl.admin.article.Block
import info.unterrainer.presserl.admin.article.Body
import info.unterrainer.presserl.admin.article.Run

/** Whether a piece of text is in both versions, only in the newer one or only in the older one. */
enum class DiffKind { SAME, ADDED, REMOVED }

/** A piece of text of a comparison; neighbouring pieces differ in [kind]. */
data class DiffPart(val kind: DiffKind, val text: String)

/** Words and the whitespace between them, each a token of its own, so changed spacing does not hide a word. */
internal fun tokens(text: String): List<String> = TOKEN.findAll(text).map { it.value }.toList()

private val TOKEN = Regex("""\s+|\S+""")

/**
 * The word changes from [old] to [new]: removed words, added words and the words in both, in text order, neighbouring
 * pieces of the same kind merged. Identical texts give one [DiffKind.SAME] piece (none for two empty texts).
 */
fun wordDiff(old: String, new: String): List<DiffPart> {
    val parts = mutableListOf<DiffPart>()
    shortestEdit(tokens(old), tokens(new)).forEach { (kind, token) ->
        val last = parts.lastOrNull()
        if (last != null && last.kind == kind) parts[parts.lastIndex] = last.copy(text = last.text + token) else parts += DiffPart(kind, token)
    }
    return parts
}

/** Whether a comparison shows any change. */
fun List<DiffPart>.changed(): Boolean = any { it.kind != DiffKind.SAME }

/**
 * A shortest edit script from [a] to [b] (Myers' O(ND) algorithm): every element of both lists in order, marked as
 * kept, added (from [b]) or removed (from [a]); within a change the removals come first.
 */
internal fun <T> shortestEdit(a: List<T>, b: List<T>): List<Pair<DiffKind, T>> {
    val n = a.size
    val m = b.size
    val max = n + m
    if (max == 0) return emptyList()
    val offset = max + 1
    var v = IntArray(2 * max + 3)
    val trace = mutableListOf<IntArray>()
    var found = -1
    search@ for (d in 0..max) {
        trace += v.copyOf()
        for (k in -d..d step 2) {
            var x = if (k == -d || (k != d && v[k - 1 + offset] < v[k + 1 + offset])) v[k + 1 + offset] else v[k - 1 + offset] + 1
            var y = x - k
            while (x < n && y < m && a[x] == b[y]) {
                x++
                y++
            }
            v[k + offset] = x
            if (x >= n && y >= m) {
                found = d
                break@search
            }
        }
    }
    val script = ArrayDeque<Pair<DiffKind, T>>()
    var x = n
    var y = m
    for (d in found downTo 0) {
        v = trace[d]
        val k = x - y
        val previousK = if (k == -d || (k != d && v[k - 1 + offset] < v[k + 1 + offset])) k + 1 else k - 1
        val previousX = v[previousK + offset]
        val previousY = previousX - previousK
        while (x > previousX && y > previousY) {
            script.addFirst(DiffKind.SAME to a[x - 1])
            x--
            y--
        }
        if (d > 0) {
            if (x == previousX) script.addFirst(DiffKind.ADDED to b[y - 1]) else script.addFirst(DiffKind.REMOVED to a[x - 1])
        }
        x = previousX
        y = previousY
    }
    return removalsFirst(script)
}

/** Within each run of changes, removals before additions, so a replaced word reads "old new". */
private fun <T> removalsFirst(script: List<Pair<DiffKind, T>>): List<Pair<DiffKind, T>> {
    val result = mutableListOf<Pair<DiffKind, T>>()
    var index = 0
    while (index < script.size) {
        if (script[index].first == DiffKind.SAME) {
            result += script[index++]
            continue
        }
        val run = mutableListOf<Pair<DiffKind, T>>()
        while (index < script.size && script[index].first != DiffKind.SAME) run += script[index++]
        result += run.filter { it.first == DiffKind.REMOVED } + run.filter { it.first == DiffKind.ADDED }
    }
    return result
}

/** The plain text of a block: runs joined, list items one per line, an image's caption. */
fun Block.plainText(): String = when (this) {
    is Block.Paragraph -> content.plain()
    is Block.Subhead -> text
    is Block.Quote -> content.plain()
    is Block.BulletList -> items.joinToString("\n") { it.plain() }
    is Block.Image -> caption
}

private fun List<Run>.plain(): String = joinToString("") { it.text }

/** How a body block changed between two revisions. */
sealed interface BlockChange {
    /** The block is in both revisions, unchanged. */
    data class Same(val block: Block) : BlockChange

    /** The block is new in the newer revision. */
    data class Added(val block: Block) : BlockChange

    /** The block was removed in the newer revision. */
    data class Removed(val block: Block) : BlockChange

    /**
     * A block of the same type changed in place: [parts] are the word changes of its text (the caption of an image),
     * [imageReplaced] tells that an image block shows another media.
     */
    data class Changed(val old: Block, val new: Block, val parts: List<DiffPart>, val imageReplaced: Boolean = false) : BlockChange
}

private fun Block.kind(): String = when (this) {
    is Block.Paragraph -> "paragraph"
    is Block.Subhead -> "subhead"
    is Block.Quote -> "quote"
    is Block.BulletList -> "list"
    is Block.Image -> "image"
}

/** What makes two blocks equal for the alignment: type, text and, for images, the media. */
private data class BlockKey(val kind: String, val text: String, val mediaId: Long?)

private fun Block.key() = BlockKey(kind(), plainText(), (this as? Block.Image)?.mediaId)

/**
 * The body changes from [old] to [new]: the blocks are aligned by type and text (a longest common subsequence);
 * between two aligned blocks, removed and added blocks of the same type are paired in order as changed blocks, the
 * rest stay removed (listed first) or added.
 */
fun blockDiff(old: List<Block>, new: List<Block>): List<BlockChange> {
    val oldByKey = old.map { it.key() to it }
    val newByKey = new.map { it.key() to it }
    val script = shortestEdit(oldByKey.map { it.first }, newByKey.map { it.first })
    val result = mutableListOf<BlockChange>()
    var oldIndex = 0
    var newIndex = 0
    var index = 0
    while (index < script.size) {
        if (script[index].first == DiffKind.SAME) {
            result += BlockChange.Same(new[newIndex])
            oldIndex++
            newIndex++
            index++
            continue
        }
        val removed = mutableListOf<Block>()
        val added = mutableListOf<Block>()
        while (index < script.size && script[index].first != DiffKind.SAME) {
            if (script[index].first == DiffKind.REMOVED) removed += old[oldIndex++] else added += new[newIndex++]
            index++
        }
        result += pairGap(removed, added)
    }
    return result
}

private fun pairGap(removed: List<Block>, added: List<Block>): List<BlockChange> {
    val unpaired = removed.toMutableList()
    val changes = added.map { block ->
        val partner = unpaired.firstOrNull { it.kind() == block.kind() }
        if (partner == null) {
            BlockChange.Added(block)
        } else {
            unpaired.remove(partner)
            BlockChange.Changed(
                partner,
                block,
                wordDiff(partner.plainText(), block.plainText()),
                imageReplaced = partner is Block.Image && block is Block.Image && partner.mediaId != block.mediaId,
            )
        }
    }
    return unpaired.map { BlockChange.Removed(it) } + changes
}

/** The header fields of a revision compared. */
enum class ComparedField { KICKER, HEADLINE, SUBHEADLINE, LEAD, LEAD_IMAGE_CAPTION }

/**
 * The comparison of [newer] with [older]: word changes per header field and of the lead-image caption, whether the
 * lead image was replaced, and the body changes.
 */
data class RevisionComparison(
    val older: RevisionDto,
    val newer: RevisionDto,
    val fields: Map<ComparedField, List<DiffPart>>,
    val leadImageReplaced: Boolean,
    val blocks: List<BlockChange>,
)

fun compare(older: RevisionDto, newer: RevisionDto): RevisionComparison = RevisionComparison(
    older,
    newer,
    fields = mapOf(
        ComparedField.KICKER to wordDiff(older.kicker, newer.kicker),
        ComparedField.HEADLINE to wordDiff(older.headline, newer.headline),
        ComparedField.SUBHEADLINE to wordDiff(older.subheadline, newer.subheadline),
        ComparedField.LEAD to wordDiff(older.lead, newer.lead),
        ComparedField.LEAD_IMAGE_CAPTION to wordDiff(older.leadImage?.caption.orEmpty(), newer.leadImage?.caption.orEmpty()),
    ),
    leadImageReplaced = older.leadImage?.mediaId != newer.leadImage?.mediaId,
    blocks = blockDiff(Body.fromJson(older.body).blocks, Body.fromJson(newer.body).blocks),
)

/** Loads revision [number] and its predecessor with [load] and compares them; [number] must be above 1. */
suspend fun loadComparison(number: Int, load: suspend (Int) -> RevisionDto): RevisionComparison {
    require(number > 1) { "revision $number has no predecessor" }
    val newer = load(number)
    val older = load(number - 1)
    return compare(older, newer)
}
