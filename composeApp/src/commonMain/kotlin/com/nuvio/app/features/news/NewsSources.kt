package com.nuvio.app.features.news

internal data class PublisherFeed(val publisher: String, val url: String)

/**
 * The publishers' own public RSS feeds. Checked live: CBS and FOX serve every sport from a few
 * general feeds (their per-league feeds mostly return the same stories), Sky has one news feed,
 * BBC has real per-sport feeds, and NBC Sports' working feed is Pro Football Talk.
 */
internal object NewsSources {
    private const val CBS = "https://www.cbssports.com/rss/headlines"
    private const val BBC = "https://feeds.bbci.co.uk/sport"
    private const val FOX_KEY = "MB0Wehpmuj2lUhuRhQaafhBjAJqaPU244mlTDK1i"

    val feeds: List<PublisherFeed> = listOf(
        PublisherFeed("CBS Sports", "$CBS/"),
        PublisherFeed("CBS Sports", "$CBS/nfl"),
        PublisherFeed("CBS Sports", "$CBS/nba"),
        PublisherFeed("CBS Sports", "$CBS/mlb"),
        PublisherFeed("CBS Sports", "$CBS/soccer"),
        PublisherFeed("CBS Sports", "$CBS/golf"),
        PublisherFeed("CBS Sports", "$CBS/tennis"),
        PublisherFeed("CBS Sports", "$CBS/college-football"),
        PublisherFeed("FOX Sports", "https://api.foxsports.com/v2/content/rss?partnerKey=$FOX_KEY&tag=nfl"),
        PublisherFeed("Sky Sports", "https://www.skysports.com/rss/12040"),
        PublisherFeed("BBC Sport", "$BBC/rss.xml"),
        PublisherFeed("BBC Sport", "$BBC/football/rss.xml"),
        PublisherFeed("BBC Sport", "$BBC/formula1/rss.xml"),
        PublisherFeed("BBC Sport", "$BBC/tennis/rss.xml"),
        PublisherFeed("BBC Sport", "$BBC/golf/rss.xml"),
        PublisherFeed("NBC Sports", "https://profootballtalk.nbcsports.com/feed/"),
    )

    private const val ACCEPT = "application/rss+xml, application/atom+xml, application/xml, text/xml"

    suspend fun fetch(feed: PublisherFeed): List<NewsArticle> {
        val xml = NewsHttp.get(feed.url, ACCEPT) ?: return emptyList()
        return RssParser.parse(xml, maxItems = 60).items.mapNotNull { toArticle(feed.publisher, it) }
    }

    private fun toArticle(publisher: String, item: RssItem): NewsArticle? {
        val url = item.link ?: item.guid?.takeIf { it.startsWith("http") } ?: return null
        val headline = item.title.removeSuffix(" - $publisher").trim()
        if (NewsClassifier.isPromo(url, headline)) return null
        val tag = NewsClassifier.classify(url, headline, item.summary, item.categories)
        return NewsArticle(
            id = "rss:" + (item.guid ?: url).hashCode().toUInt().toString(16),
            headline = headline,
            summary = item.summary,
            imageUrl = item.imageUrl?.let { sharpen(it) },
            url = url,
            publishedMs = item.publishedMs,
            leagueId = tag?.leagueId,
            leagueShort = tag?.short,
            publisher = publisher,
            author = item.author,
            sportKey = tag?.sportKey,
        )
    }

    /** BBC's feed thumbnails are 240 px wide; the same image is served larger by changing the path. */
    private fun sharpen(url: String): String =
        if ("ichef.bbci.co.uk" in url) url.replace("/standard/240/", "/standard/976/").replace("/standard/144/", "/standard/976/") else url
}
