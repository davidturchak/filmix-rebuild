package net.filmix.core.model

/**
 * Whether a Filmix avatar field names a real picture.
 *
 * The backend answers with an absolute URL when the account has uploaded one
 * — `http://thumbs.filmixapp.cyou/fotos/foto_<id>.jpg` — and with the *site*
 * path of the placeholder when it has not:
 * `/templates/Filmix/dleimages/noavatar.png`. That path is relative to the
 * website, not to the API host, so handing it to an image loader is a request
 * that cannot succeed; and even if it did, it would draw the site's grey
 * silhouette instead of ours.
 *
 * So the placeholder is read as "no avatar" and the caller draws its own. Both
 * the comment list and the profile go through here, because the rule is the
 * backend's convention rather than either screen's.
 */
object Avatar {

    /** The picture to load, or null when the account has none. */
    fun urlOrNull(field: String?): String? = field?.takeIf { it.startsWith("http") }
}
