export type Description = string;
export interface ExampleQuery2Result {
  reviewScore: FilmReviewScore
  streamingProvider: StreamingProviderName
}
export interface ExampleQuery3Result {
  name: Title
  id: FilmId
  description: Description
  platformName: StreamingProviderName
  price: PricerPerMonth
  rating: FilmReviewScore
  review: ReviewText
}
export interface ExampleQuery5Result {
  announcement: NewFilmReleaseAnnouncement
  name: Title
  id: FilmId
  description: Description
  platformName: StreamingProviderName
  price: PricerPerMonth
  rating: FilmReviewScore
  review: ReviewText
}
export interface Film {
  film_id: FilmId
  title: Title
  description: Description
  release_year: ReleaseYear
  language_id: LanguageId
  original_language_id: OriginalLanguageId
  rental_duration: RentalDuration
  rental_rate: RentalRate
  length: Length
  replacement_cost: ReplacementCost
  rating: Rating
  last_update: LastUpdate
  special_features: SpecialFeatures[]
  fulltext: Fulltext
}
export type FilmId = number;
export type FilmReviewScore = number;
export type Fulltext = string;
export type LanguageId = number;
export type LastUpdate = string;
export type Length = number;
export type NetflixAnnouncement = string;
export type NetflixFilmId = number;
export interface NewFilmReleaseAnnouncement {
  filmId: NetflixFilmId
  announcement: NetflixAnnouncement
}
export type OriginalLanguageId = number;
export type PricerPerMonth = number;
export type Rating = string;
export type ReleaseYear = number;
export type RentalDuration = number;
export type RentalRate = number;
export type ReplacementCost = number;
export type ReviewText = string;
export type SpecialFeatures = string;
export type StreamingProviderName = string;
export type Title = string;