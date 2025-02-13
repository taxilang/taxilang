import {useLazyQuery} from '@orbitalhq/taxiql-client';
import {taxiQl} from '../generated/queries.ts';
import markdown from '../markdown/QueryExample1.md?raw'
import {QueryContainer} from '../QueryContainer.tsx';
import {ResultsContainer} from '../ResultsContainer.tsx';

const queryExample1 = taxiQl(`
query ExampleQuery1 {
  find { film.Film[] }
}`)

export const QueryExample1 = () => {
  const [executeQuery, queryResult] = useLazyQuery(queryExample1);

  return (
    <>
      <QueryContainer
        query={queryExample1}
        readme={markdown}
        executeQuery={executeQuery}
        //streamQuery={streamQuery}
      />
      <ResultsContainer
        items={queryResult.data || []}
        loading={queryResult.loading}
        error={queryResult.error}
      />
    </>
  )
};
