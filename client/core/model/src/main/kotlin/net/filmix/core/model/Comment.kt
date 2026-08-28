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
 * matching the original app's one-indent rendering.
 *
 * Malformed payloads degrade instead of breaking the page: a duplicated id
 * keeps only its first occurrence (the UI keys lazy rows by root id, so
 * duplicates would crash the screen), a reply whose parent is missing
 * becomes a root, and members of a detached `parent_id` cycle (a comment
 * answering itself, or a loop no root reaches) surface as roots after the
 * real ones rather than disappearing.
 */
fun threadComments(comments: List<Comment>): List<CommentThread> {
    val unique = comments.distinctBy { it.id }
    val ids = unique.mapTo(HashSet()) { it.id }
    val byParent = unique
        .filter { it.parentId != 0 && it.parentId != it.id }
        .groupBy { it.parentId }

    // Global across roots: each comment lands in exactly one thread, and a
    // cyclic parent_id chain in a malformed payload cannot loop.
    val seen = HashSet<Int>()

    fun thread(root: Comment): CommentThread {
        seen.add(root.id)
        val replies = mutableListOf<Comment>()
        val frontier = ArrayDeque<Int>().apply { addLast(root.id) }
        while (frontier.isNotEmpty()) {
            for (child in byParent[frontier.removeLast()].orEmpty()) {
                if (seen.add(child.id)) {
                    replies += child
                    frontier.addLast(child.id)
                }
            }
        }
        return CommentThread(root, replies.sortedBy { it.id })
    }

    val threads = unique
        .filter { it.parentId == 0 || it.parentId !in ids || it.parentId == it.id }
        .mapTo(mutableListOf()) { thread(it) }
    // Anything still unseen sits in a cycle no root reaches; promote it.
    // Checked per iteration: promoting one member absorbs its cycle-mates
    // as replies, and they must not surface as roots of their own.
    for (comment in unique) {
        if (comment.id !in seen) threads += thread(comment)
    }
    return threads
}
