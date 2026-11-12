package com.nuvio.app.features.search

/** A film in a collection, found on TMDB by its title and year. */
internal class FranchiseFilm(val title: String, val year: Int)

/** A titled run of films on a collection page ("The Skywalker Saga", "Phase One"). */
internal class FilmSection(val title: String, val films: List<FranchiseFilm>)

/**
 * One curated collection page: key art across the top, then titled sections of posters. A franchise lists its
 * films by hand; the others (90 Minutes or Less, Hidden Gems, One-Night Binges) fill sections from TMDB.
 */
internal class CollectionDef(
    val id: String,
    val title: String,
    /** The film or series whose artwork is the page's hero picture, chosen by hand. */
    val heroTitle: String,
    val heroYear: Int,
    val heroIsSeries: Boolean = false,
    val filmSections: List<FilmSection> = emptyList(),
    /** The same films in story order, for franchises where that matters; null when there is only one order. */
    val chronological: List<FilmSection>? = null,
    val discoverSections: List<BrowseSpec> = emptyList(),
)

private fun f(title: String, year: Int) = FranchiseFilm(title, year)

/** The franchises on the Movie Universes page, in the order they are shown. */
internal val FRANCHISES: List<CollectionDef> = listOf(
    CollectionDef(
        id = "mcu",
        title = "Marvel Cinematic Universe",
        heroTitle = "Avengers: Endgame", heroYear = 2019,
        filmSections = listOf(
            FilmSection("Phase One", listOf(f("Iron Man", 2008), f("The Incredible Hulk", 2008), f("Iron Man 2", 2010), f("Thor", 2011), f("Captain America: The First Avenger", 2011), f("The Avengers", 2012))),
            FilmSection("Phase Two", listOf(f("Iron Man 3", 2013), f("Thor: The Dark World", 2013), f("Captain America: The Winter Soldier", 2014), f("Guardians of the Galaxy", 2014), f("Avengers: Age of Ultron", 2015), f("Ant-Man", 2015))),
            FilmSection(
                "Phase Three",
                listOf(
                    f("Captain America: Civil War", 2016), f("Doctor Strange", 2016), f("Guardians of the Galaxy Vol. 2", 2017), f("Spider-Man: Homecoming", 2017),
                    f("Thor: Ragnarok", 2017), f("Black Panther", 2018), f("Avengers: Infinity War", 2018), f("Ant-Man and the Wasp", 2018),
                    f("Captain Marvel", 2019), f("Avengers: Endgame", 2019), f("Spider-Man: Far From Home", 2019),
                ),
            ),
            FilmSection(
                "Phase Four",
                listOf(
                    f("Black Widow", 2021), f("Shang-Chi and the Legend of the Ten Rings", 2021), f("Eternals", 2021), f("Spider-Man: No Way Home", 2021),
                    f("Doctor Strange in the Multiverse of Madness", 2022), f("Thor: Love and Thunder", 2022), f("Black Panther: Wakanda Forever", 2022),
                ),
            ),
            FilmSection(
                "Phase Five",
                listOf(
                    f("Ant-Man and the Wasp: Quantumania", 2023), f("Guardians of the Galaxy Vol. 3", 2023), f("The Marvels", 2023),
                    f("Deadpool & Wolverine", 2024), f("Captain America: Brave New World", 2025), f("Thunderbolts*", 2025),
                ),
            ),
            FilmSection("Phase Six", listOf(f("The Fantastic Four: First Steps", 2025), f("Spider-Man: Brand New Day", 2026), f("Avengers: Doomsday", 2026))),
        ),
        chronological = listOf(
            FilmSection(
                "Chronological Order",
                listOf(
                    f("Captain America: The First Avenger", 2011), f("Captain Marvel", 2019), f("Iron Man", 2008), f("Iron Man 2", 2010),
                    f("The Incredible Hulk", 2008), f("Thor", 2011), f("The Avengers", 2012), f("Thor: The Dark World", 2013),
                    f("Iron Man 3", 2013), f("Captain America: The Winter Soldier", 2014), f("Guardians of the Galaxy", 2014),
                    f("Guardians of the Galaxy Vol. 2", 2017), f("Avengers: Age of Ultron", 2015), f("Ant-Man", 2015),
                    f("Captain America: Civil War", 2016), f("Black Widow", 2021), f("Black Panther", 2018),
                    f("Spider-Man: Homecoming", 2017), f("Doctor Strange", 2016), f("Thor: Ragnarok", 2017),
                    f("Ant-Man and the Wasp", 2018), f("Avengers: Infinity War", 2018), f("Avengers: Endgame", 2019),
                    f("Spider-Man: Far From Home", 2019), f("Shang-Chi and the Legend of the Ten Rings", 2021), f("Eternals", 2021),
                    f("Spider-Man: No Way Home", 2021), f("Doctor Strange in the Multiverse of Madness", 2022),
                    f("Black Panther: Wakanda Forever", 2022), f("Thor: Love and Thunder", 2022),
                    f("Ant-Man and the Wasp: Quantumania", 2023), f("Guardians of the Galaxy Vol. 3", 2023), f("The Marvels", 2023),
                    f("Deadpool & Wolverine", 2024), f("Captain America: Brave New World", 2025), f("Thunderbolts*", 2025),
                    f("The Fantastic Four: First Steps", 2025), f("Spider-Man: Brand New Day", 2026), f("Avengers: Doomsday", 2026),
                ),
            ),
        ),
    ),
    CollectionDef(
        id = "starwars",
        title = "Star Wars",
        heroTitle = "Star Wars: The Rise of Skywalker", heroYear = 2019,
        filmSections = listOf(
            FilmSection(
                "The Skywalker Saga",
                listOf(
                    f("Star Wars", 1977), f("The Empire Strikes Back", 1980), f("Return of the Jedi", 1983),
                    f("Star Wars: Episode I - The Phantom Menace", 1999), f("Star Wars: Episode II - Attack of the Clones", 2002),
                    f("Star Wars: Episode III - Revenge of the Sith", 2005), f("Star Wars: The Force Awakens", 2015),
                    f("Star Wars: The Last Jedi", 2017), f("Star Wars: The Rise of Skywalker", 2019),
                ),
            ),
            FilmSection("Star Wars Stories", listOf(f("Rogue One: A Star Wars Story", 2016), f("Solo: A Star Wars Story", 2018), f("The Mandalorian and Grogu", 2026))),
        ),
    ),
    CollectionDef(
        id = "wizarding",
        title = "Wizarding World",
        heroTitle = "Harry Potter and the Philosopher's Stone", heroYear = 2001,
        filmSections = listOf(
            FilmSection(
                "Harry Potter",
                listOf(
                    f("Harry Potter and the Philosopher's Stone", 2001), f("Harry Potter and the Chamber of Secrets", 2002),
                    f("Harry Potter and the Prisoner of Azkaban", 2004), f("Harry Potter and the Goblet of Fire", 2005),
                    f("Harry Potter and the Order of the Phoenix", 2007), f("Harry Potter and the Half-Blood Prince", 2009),
                    f("Harry Potter and the Deathly Hallows: Part 1", 2010), f("Harry Potter and the Deathly Hallows: Part 2", 2011),
                ),
            ),
            FilmSection(
                "Fantastic Beasts",
                listOf(f("Fantastic Beasts and Where to Find Them", 2016), f("Fantastic Beasts: The Crimes of Grindelwald", 2018), f("Fantastic Beasts: The Secrets of Dumbledore", 2022)),
            ),
        ),
    ),
    CollectionDef(
        id = "middleearth",
        title = "Middle-earth",
        heroTitle = "The Lord of the Rings: The Fellowship of the Ring", heroYear = 2001,
        filmSections = listOf(
            FilmSection(
                "The Lord of the Rings",
                listOf(f("The Lord of the Rings: The Fellowship of the Ring", 2001), f("The Lord of the Rings: The Two Towers", 2002), f("The Lord of the Rings: The Return of the King", 2003)),
            ),
            FilmSection(
                "The Hobbit",
                listOf(f("The Hobbit: An Unexpected Journey", 2012), f("The Hobbit: The Desolation of Smaug", 2013), f("The Hobbit: The Battle of the Five Armies", 2014)),
            ),
            FilmSection("More from Middle-earth", listOf(f("The Lord of the Rings: The War of the Rohirrim", 2024))),
        ),
    ),
    CollectionDef(
        id = "dc",
        title = "DC Universes",
        heroTitle = "Justice League", heroYear = 2017,
        filmSections = listOf(
            FilmSection("DC Universe (DCU)", listOf(f("Superman", 2025), f("Supergirl", 2026), f("Clayface", 2026))),
            FilmSection(
                "DC Extended Universe (DCEU)",
                listOf(
                    f("Man of Steel", 2013), f("Batman v Superman: Dawn of Justice", 2016), f("Suicide Squad", 2016),
                    f("Wonder Woman", 2017), f("Justice League", 2017), f("Aquaman", 2018), f("Shazam!", 2019),
                    f("Birds of Prey (and the Fantabulous Emancipation of One Harley Quinn)", 2020), f("Wonder Woman 1984", 2020),
                    f("The Suicide Squad", 2021), f("Black Adam", 2022), f("Shazam! Fury of the Gods", 2023),
                    f("The Flash", 2023), f("Blue Beetle", 2023), f("Aquaman and the Lost Kingdom", 2023),
                ),
            ),
            FilmSection("Burton & Schumacher Batman", listOf(f("Batman", 1989), f("Batman Returns", 1992), f("Batman Forever", 1995), f("Batman & Robin", 1997))),
            FilmSection("The Dark Knight Trilogy", listOf(f("Batman Begins", 2005), f("The Dark Knight", 2008), f("The Dark Knight Rises", 2012))),
            FilmSection("The Batman Universe", listOf(f("The Batman", 2022), f("Joker", 2019), f("Joker: Folie à Deux", 2024))),
        ),
    ),
    CollectionDef(
        id = "jurassic",
        title = "Jurassic Park / World",
        heroTitle = "Jurassic Park", heroYear = 1993,
        filmSections = listOf(
            FilmSection("Jurassic Park", listOf(f("Jurassic Park", 1993), f("The Lost World: Jurassic Park", 1997), f("Jurassic Park III", 2001))),
            FilmSection("Jurassic World", listOf(f("Jurassic World", 2015), f("Jurassic World: Fallen Kingdom", 2018), f("Jurassic World Dominion", 2022), f("Jurassic World Rebirth", 2025))),
        ),
    ),
    CollectionDef(
        id = "fast",
        title = "Fast & Furious",
        heroTitle = "Furious 7", heroYear = 2015,
        filmSections = listOf(
            FilmSection(
                "The Fast Saga",
                listOf(
                    f("The Fast and the Furious", 2001), f("2 Fast 2 Furious", 2003), f("The Fast and the Furious: Tokyo Drift", 2006),
                    f("Fast & Furious", 2009), f("Fast Five", 2011), f("Fast & Furious 6", 2013), f("Furious 7", 2015),
                    f("The Fate of the Furious", 2017), f("F9", 2021), f("Fast X", 2023),
                ),
            ),
            FilmSection("Spin-offs", listOf(f("Fast & Furious Presents: Hobbs & Shaw", 2019))),
        ),
    ),
    CollectionDef(
        id = "conjuring",
        title = "The Conjuring Universe",
        heroTitle = "The Conjuring", heroYear = 2013,
        filmSections = listOf(
            FilmSection(
                "The Conjuring",
                listOf(f("The Conjuring", 2013), f("The Conjuring 2", 2016), f("The Conjuring: The Devil Made Me Do It", 2021), f("The Conjuring: Last Rites", 2025)),
            ),
            FilmSection("Annabelle", listOf(f("Annabelle", 2014), f("Annabelle: Creation", 2017), f("Annabelle Comes Home", 2019))),
            FilmSection("The Nun and More", listOf(f("The Nun", 2018), f("The Curse of La Llorona", 2019), f("The Nun II", 2023))),
        ),
    ),
)

private fun discover(
    id: String,
    label: String,
    endpoint: String,
    base: Map<String, String>,
    genres: String? = null,
    tv: Boolean = false,
) = BrowseSpec(
    key = "$id-$label",
    title = label,
    endpoint = endpoint,
    query = if (genres == null) base else base + ("with_genres" to genres),
    tv = tv,
)

// TMDB's own adult flag misses some explicit films, so the explicit-content keywords (softcore, erotic, explicit sex,
// nudity) are excluded as well, and ratings are capped at R.
private const val EXPLICIT_KEYWORDS = "155477|190370|347060|256466|155691|281741"

private val UNDER_90_BASE = mapOf(
    "with_runtime.gte" to "60", "with_runtime.lte" to "89", "sort_by" to "popularity.desc",
    "vote_count.gte" to "400", "include_adult" to "false",
    "certification_country" to "US", "certification.lte" to "R", "without_keywords" to EXPLICIT_KEYWORDS,
)
// Highly rated and seen by enough people to trust the score, but nowhere near the blockbusters: a mid-sized vote count
// with a high average. TMDB's popularity is this week's buzz, which would put brand-new releases first, so the list is
// sorted by rating and limited to films at least a couple of years old; the franchise de-duplication then keeps the
// picks varied.
private val HIDDEN_GEMS_BASE = mapOf(
    "vote_average.gte" to "7.4", "vote_count.gte" to "1500", "vote_count.lte" to "6500",
    "sort_by" to "vote_average.desc", "include_adult" to "false", "without_genres" to "99,10770,10749,16",
    "with_original_language" to "en", "primary_release_date.lte" to "{TWO_YEARS_AGO}", "without_keywords" to EXPLICIT_KEYWORDS,
)

private val BINGE_BASE = mapOf(
    "with_type" to "2", "sort_by" to "popularity.desc", "vote_count.gte" to "200", "include_adult" to "false",
    "without_keywords" to EXPLICIT_KEYWORDS,
)

/** The three collections that are filled from TMDB, each as titled sections rather than one long list. */
internal val DISCOVER_COLLECTIONS: List<CollectionDef> = listOf(
    CollectionDef(
        id = "under90", title = "90 Minutes or Less",
        heroTitle = "Toy Story", heroYear = 1995,
        discoverSections = listOf(
            discover("u90", "Most Popular", "discover/movie", UNDER_90_BASE),
            discover("u90", "Comedies", "discover/movie", UNDER_90_BASE, "35"),
            discover("u90", "Thrillers", "discover/movie", UNDER_90_BASE, "53"),
            discover("u90", "Horror", "discover/movie", UNDER_90_BASE, "27"),
            discover("u90", "Animation", "discover/movie", UNDER_90_BASE, "16"),
            discover("u90", "Sci-Fi", "discover/movie", UNDER_90_BASE, "878"),
        ),
    ),
    CollectionDef(
        id = "gems", title = "Hidden Gems",
        heroTitle = "Moon", heroYear = 2009,
        discoverSections = listOf(
            discover("gem", "Highly Rated, Under the Radar", "discover/movie", HIDDEN_GEMS_BASE),
            discover("gem", "Drama", "discover/movie", HIDDEN_GEMS_BASE, "18"),
            discover("gem", "Thrillers", "discover/movie", HIDDEN_GEMS_BASE, "53"),
            discover("gem", "Sci-Fi", "discover/movie", HIDDEN_GEMS_BASE, "878"),
            discover("gem", "Comedies", "discover/movie", HIDDEN_GEMS_BASE, "35"),
            discover("gem", "Crime", "discover/movie", HIDDEN_GEMS_BASE, "80"),
        ),
    ),
    CollectionDef(
        id = "binges", title = "One-Night Binges",
        heroTitle = "Chernobyl", heroYear = 2019, heroIsSeries = true,
        discoverSections = listOf(
            discover("bng", "Most Popular", "discover/tv", BINGE_BASE, tv = true),
            discover("bng", "Crime & Mystery", "discover/tv", BINGE_BASE, "80|9648", tv = true),
            discover("bng", "Drama", "discover/tv", BINGE_BASE, "18", tv = true),
            discover("bng", "Sci-Fi & Fantasy", "discover/tv", BINGE_BASE, "10765", tv = true),
            discover("bng", "Comedy", "discover/tv", BINGE_BASE, "35", tv = true),
        ),
    ),
)

/** Every collection page by id: the three TMDB-filled ones and the franchises. */
internal val COLLECTION_DEFS: Map<String, CollectionDef> = (DISCOVER_COLLECTIONS + FRANCHISES).associateBy { it.id }
