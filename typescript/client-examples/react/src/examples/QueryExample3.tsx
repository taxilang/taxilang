import {useLazyQuery} from '@orbitalhq/taxiql-client';
import {taxiQl} from '../generated/queries.ts';
import markdown from '../markdown/QueryExample3.md?raw'
import {QueryContainer} from '../QueryContainer.tsx';
import {ResultsContainer} from '../ResultsContainer.tsx';

const queryExample3 = taxiQl(`
query ExampleQuery3 {
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
}`)

export const QueryExample3 = () => {
  const [executeQuery, queryResult] = useLazyQuery(queryExample3);

  return (
    <>
      <QueryContainer
        query={queryExample3}
        readme={markdown}
        executeQuery={executeQuery}
      />
      <ResultsContainer
        items={queryResult.data || []}
        loading={queryResult.loading}
        error={queryResult.error}
      />
    </>
  )
};
