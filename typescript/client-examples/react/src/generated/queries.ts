import { TypedQuery } from '@orbitalhq/taxiql-client';
import { Film } from './types';
import { TypedStream } from '@orbitalhq/taxiql-client';
import { NewFilmReleaseAnnouncement } from './types';
import { ExampleQuery2Result } from './types';
import { ExampleQuery3Result } from './types';
import { ExampleQuery5Result } from './types';

export const HooksShowcaseQuery = {
   taxiQl: `query HooksShowcaseQuery {
  find { Film[] }
}`
} as TypedQuery<Film[]>;

export const HooksShowcaseStream = {
   taxiQl: `query HooksShowcaseStream {
  stream { demo.netflix.NewFilmReleaseAnnouncement }
}`
} as TypedStream<NewFilmReleaseAnnouncement>;

export const ExampleQuery1 = {
   taxiQl: `query ExampleQuery1 {
  find { film.Film[] }
}`
} as TypedQuery<Film[]>;

export const ExampleQuery2 = {
   taxiQl: `query ExampleQuery2 {
  given { films.FilmId = 5 }
  find { film.Film[] } as {
    reviewScore : films.reviews.FilmReviewScore
    streamingProvider : io.vyne.films.providers.StreamingProviderName
  }[]
}`
} as TypedQuery<ExampleQuery2Result[]>;

export const ExampleQuery3 = {
   taxiQl: `query ExampleQuery3 {
  find { Film[] } as {
    name: film.Title id : films.FilmId
    description: film.Description
    /* Where can I watch this? */
    platformName: io.vyne.films.providers.StreamingProviderName
    price: io.vyne.films.providers.PricerPerMonth
    /* Grab some reviews */
    rating: films.reviews.FilmReviewScore
    review: films.reviews.ReviewText
  }[]
}`
} as TypedQuery<ExampleQuery3Result[]>;

export const ExampleQuery4 = {
   taxiQl: `query ExampleQuery4 {
  stream { demo.netflix.NewFilmReleaseAnnouncement }
}`
} as TypedStream<NewFilmReleaseAnnouncement>;

export const ExampleQuery5 = {
   taxiQl: `query ExampleQuery5 {
  stream { NewFilmReleaseAnnouncement } as {
    announcement: NewFilmReleaseAnnouncement
    name: Title id : FilmId
    description: Description
    platformName: StreamingProviderName
    price: PricerPerMonth
    rating: FilmReviewScore
    review: ReviewText
  }[]
}`
} as TypedQuery<ExampleQuery5Result[]>;

export const queries = {
  "\nquery HooksShowcaseQuery {\n  find { Film[] }\n}" : HooksShowcaseQuery,
  "\nquery HooksShowcaseStream {\n  stream { demo.netflix.NewFilmReleaseAnnouncement }\n}" : HooksShowcaseStream,
  "\nquery ExampleQuery1 {\n  find { film.Film[] }\n}" : ExampleQuery1,
  "\nquery ExampleQuery2 {\n  given { films.FilmId = 5 }\n  find { film.Film[] } as {\n    reviewScore : films.reviews.FilmReviewScore\n    streamingProvider : io.vyne.films.providers.StreamingProviderName\n  }[]\n}" : ExampleQuery2,
  "\nquery ExampleQuery3 {\n  find { Film[] } as {\n    name: film.Title id : films.FilmId\n    description: film.Description\n    /* Where can I watch this? */\n    platformName: io.vyne.films.providers.StreamingProviderName\n    price: io.vyne.films.providers.PricerPerMonth\n    /* Grab some reviews */\n    rating: films.reviews.FilmReviewScore\n    review: films.reviews.ReviewText\n  }[]\n}" : ExampleQuery3,
  "\nquery ExampleQuery4 {\n  stream { demo.netflix.NewFilmReleaseAnnouncement }\n}" : ExampleQuery4,
  "\nquery ExampleQuery5 {\n  stream { NewFilmReleaseAnnouncement } as {\n    announcement: NewFilmReleaseAnnouncement\n    name: Title id : FilmId\n    description: Description\n    platformName: StreamingProviderName\n    price: PricerPerMonth\n    rating: FilmReviewScore\n    review: ReviewText\n  }[]\n}" : ExampleQuery5
}

export function taxiQl(query: "\nquery HooksShowcaseQuery {\n  find { Film[] }\n}"): TypedQuery<Film[]>
export function taxiQl(query: "\nquery HooksShowcaseStream {\n  stream { demo.netflix.NewFilmReleaseAnnouncement }\n}"): TypedStream<NewFilmReleaseAnnouncement>
export function taxiQl(query: "\nquery ExampleQuery1 {\n  find { film.Film[] }\n}"): TypedQuery<Film[]>
export function taxiQl(query: "\nquery ExampleQuery2 {\n  given { films.FilmId = 5 }\n  find { film.Film[] } as {\n    reviewScore : films.reviews.FilmReviewScore\n    streamingProvider : io.vyne.films.providers.StreamingProviderName\n  }[]\n}"): TypedQuery<ExampleQuery2Result[]>
export function taxiQl(query: "\nquery ExampleQuery3 {\n  find { Film[] } as {\n    name: film.Title id : films.FilmId\n    description: film.Description\n    /* Where can I watch this? */\n    platformName: io.vyne.films.providers.StreamingProviderName\n    price: io.vyne.films.providers.PricerPerMonth\n    /* Grab some reviews */\n    rating: films.reviews.FilmReviewScore\n    review: films.reviews.ReviewText\n  }[]\n}"): TypedQuery<ExampleQuery3Result[]>
export function taxiQl(query: "\nquery ExampleQuery4 {\n  stream { demo.netflix.NewFilmReleaseAnnouncement }\n}"): TypedStream<NewFilmReleaseAnnouncement>
export function taxiQl(query: "\nquery ExampleQuery5 {\n  stream { NewFilmReleaseAnnouncement } as {\n    announcement: NewFilmReleaseAnnouncement\n    name: Title id : FilmId\n    description: Description\n    platformName: StreamingProviderName\n    price: PricerPerMonth\n    rating: FilmReviewScore\n    review: ReviewText\n  }[]\n}"): TypedQuery<ExampleQuery5Result[]>

export function taxiQl<T>(query: string): TypedQuery<T> {
   return (queries as any)[query];
}