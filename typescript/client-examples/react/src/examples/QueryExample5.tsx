import {useLazySubscription} from '@orbitalhq/taxiql-client';
import {useState} from 'react';
import {taxiQl} from '../generated/queries.ts';
import {ExampleQuery5Result} from '../generated/types.ts';
import markdown from '../markdown/QueryExample5.md?raw'
import {QueryContainer} from '../QueryContainer.tsx';
import {ResultsContainer} from '../ResultsContainer.tsx';

const queryExample5 = taxiQl(`
query ExampleQuery5 {
  stream { NewFilmReleaseAnnouncement } as {
    announcement: NewFilmReleaseAnnouncement
    name: Title id : FilmId
    description: Description
    platformName: StreamingProviderName
    price: PricerPerMonth
    rating: FilmReviewScore
    review: ReviewText
  }[]
}`)

export const QueryExample5 = () => {
  const [accumulatedData, setAccumulatedData] = useState<ExampleQuery5Result[]>([]);
  const [streamQuery, queryResult, unsubscribe] = useLazySubscription(
    queryExample5,
    {
      onData(data: ExampleQuery5Result) {
        setAccumulatedData((prev: [ExampleQuery5Result]) => [data, ...prev])
      }
    }
  );
  return (
    <>
      <QueryContainer
        query={queryExample5}
        readme={markdown}
        streamQuery={() => {
          setAccumulatedData([])
          streamQuery()
        }}
      />
      <ResultsContainer
        items={accumulatedData || []}
        loading={queryResult.loading}
        error={queryResult.error}
        called={queryResult.called}
        closeStream={unsubscribe}
      />
    </>
  )
};
