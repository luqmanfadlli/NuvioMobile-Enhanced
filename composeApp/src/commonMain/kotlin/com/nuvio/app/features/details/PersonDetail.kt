package com.nuvio.app.features.details

import com.nuvio.app.features.home.MetaPreview

data class PersonDetail(
    val tmdbId: Int,
    val name: String,
    val biography: String?,
    val birthday: String?,
    val deathday: String?,
    val placeOfBirth: String?,
    val profilePhoto: String?,
    val knownFor: String?,
    val movieCredits: List<MetaPreview>,
    val tvCredits: List<MetaPreview>,
    /** Titles they directed (or created), most notable first. */
    val directedCredits: List<MetaPreview> = emptyList(),
    /** Titles they acted in, most notable first. */
    val actingCredits: List<MetaPreview> = emptyList(),
)
