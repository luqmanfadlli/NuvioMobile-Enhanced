package com.nuvio.app.features.player

import com.nuvio.app.features.player.skip.PlayerNextEpisodeRules
import kotlin.test.Test
import kotlin.test.assertEquals

class MovieRecommendationTimingTest {
 @Test fun apiCreditsTimestampWinsOverFallback() {
  assertEquals(0, PlayerNextEpisodeRules.movieRecommendationStage(700_000, 1_000_000, false, 800_000, 180))
  assertEquals(1, PlayerNextEpisodeRules.movieRecommendationStage(800_000, 1_000_000, false, 800_000, 180))
 }
 @Test fun chapterTimestampIsUsedWhenApiTimestampIsMissing() {
  assertEquals(1, PlayerNextEpisodeRules.movieRecommendationStage(700_000, 1_000_000, false, 700_000, 180))
 }
 @Test fun fallbackIsThreeMinutesByDefaultAndConfigurable() {
  assertEquals(0, PlayerNextEpisodeRules.movieRecommendationStage(819_999, 1_000_000, false, null, 180))
  assertEquals(1, PlayerNextEpisodeRules.movieRecommendationStage(820_000, 1_000_000, false, null, 180))
  assertEquals(1, PlayerNextEpisodeRules.movieRecommendationStage(880_000, 1_000_000, false, null, 120))
 }
 @Test fun seekingAcrossCreditsBoundaryAndEndedPlaybackShowsRecommendations() {
  assertEquals(0, PlayerNextEpisodeRules.movieRecommendationStage(499_999, 1_000_000, false, 500_000, 180))
  assertEquals(1, PlayerNextEpisodeRules.movieRecommendationStage(500_000, 1_000_000, false, 500_000, 180))
  assertEquals(1, PlayerNextEpisodeRules.movieRecommendationStage(0, 0, true, null, 180))
 }
}