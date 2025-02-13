import {useState} from 'react';
import {useLazyQuery, useLazySubscription, useQuery, useSubscription} from '@orbitalhq/taxiql-client';
import {taxiQl} from './generated/queries.ts';
import './HooksShowcase.module.css'
import {NewFilmReleaseAnnouncement} from './generated/types.ts';

const QUERY = taxiQl(`
query HooksShowcaseQuery {
  find { Film[] }
}`)

const STREAM = taxiQl(`
query HooksShowcaseStream {
  stream { demo.netflix.NewFilmReleaseAnnouncement }
}`)


function UseQueryExample() {
  const { loading, error, data } = useQuery(QUERY);

  if (loading) return <div className="loading">Loading...</div>;
  if (error) return <div>Error: {error.message}</div>;

  return (
    <div>
      <h1>Results</h1>
      {data?.map((value, index) => {
        return (
          <div key={index}>
            <span>{value.title}</span>|
            <span>{value.release_year}</span>|
            <span>{value.rating}</span>
          </div>
        )
      })}
    </div>
  )
}

function UseLazyQueryExample() {
  const [executeQuery, { loading: loadingLazy, error: errorLazy, data: dataLazy, called }] = useLazyQuery(QUERY);

  return (
    <div>
      {/* Manually fetched on button click */}
      {!called && <button onClick={executeQuery}>Fetch Data Lazily</button>}
      {loadingLazy && <div className="loading">Loading...</div>}
      {errorLazy && <div>Error: {errorLazy.message}</div>}
      {!loadingLazy && called &&
        <>
          <h1>Results</h1>
          {dataLazy?.map((value, index) => {
            return (
              <div key={index}>
                <span>{value.title}</span>|
                <span>{value.release_year}</span>|
                <span>{value.rating}</span>
              </div>
            )
          })}
        </>
      }
    </div>
  )
}

function UseSubscriptionExample() {
  const [accumulatedData, setAccumulatedData] = useState<NewFilmReleaseAnnouncement[]>([]);
  const { error, loading, called } = useSubscription(
    STREAM,
    {
      onData(data) {
        setAccumulatedData((prev) => [data, ...prev])
      }
    }
  );

  return (
    <div>
      {loading && <div className="loading">Loading...</div>}
      {error && <div>Error: {error.message}</div>}
      {!loading && called &&
        <>
          <h1>Results</h1>
          {accumulatedData?.map((value, index) => {
            return (
              <div key={index}>
                <span>{value.filmId}</span>|
                <span>{value.announcement}</span>|
              </div>
            )
          })}
        </>
      }
    </div>
  )
}

function UseLazySubscriptionExample() {
  const [accumulatedData, setAccumulatedData] = useState<NewFilmReleaseAnnouncement[]>([]);
  const [streamQuery, { error, loading, called }, unsubscribe] = useLazySubscription(
    STREAM,
    {
      onData(data) {
        setAccumulatedData((prev) => [data, ...prev])
      }
    }
  );

  return (
    <div>
      {/* Manually streamed on button click */}
      {!called && <button onClick={streamQuery}>Stream Data Lazily</button>}
      {called && <button onClick={()=> unsubscribe?.()}>Cancel stream</button>}
      {loading && <div className="loading">Loading...</div>}
      {error && <div>Error: {error.message}</div>}
      {!loading && called &&
        <>
          <h1>Results</h1>
          {accumulatedData?.map((value, index) => {
            return (
              <div key={index}>
                <span>{value.filmId}</span>|
                <span>{value.announcement}</span>|
              </div>
            )
          })}
        </>
      }
    </div>
  )
}


function HooksShowcase() {
  return (
    <>
      <UseQueryExample/>
      <UseLazyQueryExample/>
      <UseSubscriptionExample/>
      <UseLazySubscriptionExample/>
    </>
  )
}

export default HooksShowcase
