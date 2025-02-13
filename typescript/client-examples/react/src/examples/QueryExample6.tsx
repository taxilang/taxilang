import {useQuery} from '@orbitalhq/taxiql-client';
import {taxiQl} from '../generated/queries.ts';
import markdown from '../markdown/QueryExample6.md?raw'
import {QueryContainer} from '../QueryContainer.tsx';
import {ResultsContainer} from '../ResultsContainer.tsx';

const queryExample6 = taxiQl(`
query ExampleQuery1 {
  find { film.Film[] }
}`)

export const QueryExample6 = () => {
  const {data, loading, error} = useQuery(queryExample6);

  return (
    <>
      <QueryContainer
        query={queryExample6}
        readme={markdown}
      />
      <ResultsContainer
        items={data || []}
        loading={loading}
        error={error}
      />
    </>
  )
};
