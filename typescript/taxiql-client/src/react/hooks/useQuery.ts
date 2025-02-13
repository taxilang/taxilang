import {useEffect, useRef, useState} from 'react';
import {QueryResult, TypedQuery} from '../../types';
import {useTaxiClient} from './useTaxiClient';

/**
 * A hook for executing TaxiQL in a React application.
 *
 * To run a query within a React component, call `useQuery` and pass it TaxiQL.
 *
 * When your component renders, `useQuery` returns an object from Orbital that contains `loading`, `error`, and `data` properties you can use to render your UI.
 */

export function useQuery<T>(query: TypedQuery<T>): QueryResult<T> {
  const client = useTaxiClient();
  const abortControllersRef = useRef(new Set<AbortController>());

  const [queryResult, setQueryResult] = useState<QueryResult<T>>({
    data: null,
    loading: true,
    error: null,
    called: false,
  });

  useEffect(() => {
    // Prevent unnecessary requests
    if (!query) return;

    const controller = new AbortController();
    abortControllersRef.current.add(controller);

    // Perform the TaxiQl query on mount
    const fetchData = async () => {
      setQueryResult((prev) => ({ ...prev, loading: true, called: true })); // Set loading state before fetching

      try {
        const result = await client.query<T>(query, controller.signal);
        setQueryResult({ data: result, loading: false, error: null, called: true });
      } catch (error: any) {
        if (!(error instanceof Error && error.name === 'AbortError')) {
          setQueryResult((prev) => ({ ...prev, error, loading: false }));
        }
      } finally {
        abortControllersRef.current.delete(controller);
      }
    };

    fetchData();

    // Cancels the query when the React component is unmounted
    return () => {
      // Cleanup function - only abort if still present
      if (abortControllersRef.current.has(controller)) {
        controller.abort();
        abortControllersRef.current.delete(controller);
      }
    };

  }, [query]); // Runs whenever query changes.


  return queryResult;
}
