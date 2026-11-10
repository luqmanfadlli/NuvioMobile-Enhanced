package com.nuvio.app.features.home

/**
 * Catalog rows arrive named like a database ("Popular - Movies"). This words them the way a streaming app would:
 * the kind of row first and the plain word for the content ("Trending Movies", "Trending Shows").
 */
fun friendlyCatalogTitle(raw: String): String {
    val title = raw.trim()
    val split = title.split(" - ", " – ", " — ", limit = 2)
    if (split.size != 2) return title
    val kind = split[0].trim()
    val content = when (split[1].trim().lowercase()) {
        "movie", "movies" -> "Movies"
        "series", "tv", "show", "shows", "tv shows" -> "Shows"
        else -> return title
    }
    val lead = when (kind.lowercase()) {
        "popular" -> "Trending"
        "featured" -> "Handpicked"
        "top", "top rated" -> "Top Rated"
        else -> kind
    }
    return "$lead $content"
}
