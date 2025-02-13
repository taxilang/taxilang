import {useLazyQuery} from '@orbitalhq/taxiql-client';
import {taxiQl} from '../generated/queries.ts';
import markdown from '../markdown/QueryExample2.md?raw'
import {QueryContainer} from '../QueryContainer.tsx';
import {ResultsContainer} from '../ResultsContainer.tsx';

const queryExample2 = taxiQl(`
query ExampleQuery2 {
  given { films.FilmId = 5 }
  find { film.Film[] } as {
    reviewScore : films.reviews.FilmReviewScore
    streamingProvider : io.vyne.films.providers.StreamingProviderName
  }[]
}`)

export const QueryExample2 = () => {
  const [executeQuery, queryResult] = useLazyQuery(queryExample2);

  return (
    <>
      <QueryContainer
        query={queryExample2}
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
