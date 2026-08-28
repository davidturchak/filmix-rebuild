package net.filmix.core.model

/** One comment from `GET /api/v2/comments/{post_id}`. */
data class Comment(
    val id: Int,
    /** Upstream `parent_id`; 0 marks a top-level comment. */
    val parentId: Int,
    /** Pre-formatted by the server ("Вчера, 16:08", "26 авг 2026"). */
    val date: String,
    /** Upstream `gast_name`. */
    val author: String,
    val text: String,
    /** Null when the server sends its relative no-avatar placeholder path. */
    val avatarUrl: String?,
)

/** A top-level comment with all its descendants flattened to one reply level. */
data class CommentThread(
    val root: Comment,
    val replies: List<Comment>,
) {
    val size: Int get() = 1 + replies.size
}

/**
 * Rebuilds threads from the flattened payload: replies arrive in the same
 * top-level array as the comments they answer, tied together only by
 * `parent_id` (the payload's `childs` field shifts between an array and a
 * date-keyed object, so it cannot be trusted). Roots keep the array order
 * (newest-first as served); each root gathers its descendants from any
 * nesting depth into a single reply level, ascending by id — chronological,
 * matching the original app's one-indent rendering. A reply whose parent is
 * missing from the payload becomes a root rather than disappearing.
 */
fun threadComments(comments: List<Comment>): List<CommentThread> {
    val ids = comments.mapTo(HashSet()) { it.id }
    val byParent = comments.filter { it.parentId != 0 }.groupBy { it.parentId }

    // `seen` guards against a cyclic parent_id chain in a malformed payload.
    fun descendants(id: Int, seen: MutableSet<Int>): List<Comment> =
        (byParent[id] ?: emptyList())
            .filter { seen.add(it.id) }
            .flatMap { listOf(it) + descendants(it.id, seen) }

    return comments
        .filter { it.parentId == 0 || it.parentId !in ids }
        .map { root ->
            val seen = mutableSetOf(root.id)
            CommentThread(root, descendants(root.id, seen).sortedBy { it.id })
        }
}
