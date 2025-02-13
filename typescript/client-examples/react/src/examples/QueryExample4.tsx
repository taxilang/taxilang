import {useLazySubscription} from '@orbitalhq/taxiql-client';
import {useState} from 'react';
import {taxiQl} from '../generated/queries.ts';
import {NewFilmReleaseAnnouncement} from '../generated/types.ts';
import markdown from '../markdown/QueryExample4.md?raw'
import {QueryContainer} from '../QueryContainer.tsx';
import {ResultsContainer} from '../ResultsContainer.tsx';

const queryExample4 = taxiQl(`
query ExampleQuery4 {
  stream { demo.netflix.NewFilmReleaseAnnouncement }
}`)

export const QueryExample4 = () => {
  const [accumulatedData, setAccumulatedData] = useState<NewFilmReleaseAnnouncement[]>([]);
  const [streamQuery, {loading, error, called}, unsubscribe] = useLazySubscription(
    queryExample4,
    {
      onData(data) {
        setAccumulatedData((prev) => [data, ...prev])
      }
    }
  );

  return (
    <>
      <QueryContainer
        query={queryExample4}
        readme={markdown}
        streamQuery={() => {
          setAccumulatedData([])
          streamQuery()
        }}
      />
      <ResultsContainer
        items={accumulatedData || []}
        loading={loading}
        error={error}
        called={called}
        closeStream={unsubscribe}
      />
    </>
  )
};
