import {useCallback, useEffect, useRef, useState} from 'react';
import {QueryResult, TypedQuery} from '../../types';
import {useTaxiClient} from './useTaxiClient';

/**
 * A hook for imperatively executing TaxiQL queries in a React application, e.g. in response to user interaction.
 */

export function useLazyQuery<T>(
  query: TypedQuery<T>
): [() => Promise<void>, QueryResult<T>] {
  const client = useTaxiClient();
  const abortControllersRef = useRef(new Set<AbortController>());

  const [queryResult, setQueryResult] = useState<QueryResult<T>>({
    data: null,
    loading: false,
    error: null,
    called: false,
  });

  // Cancels the query when the React component is unmounted
  useEffect(() => {
    return () => {
      abortControllersRef.current.forEach((controller) => {
        controller.abort();
      });
    }
  }, [])

  const executeQuery = useCallback(async () => {
    const controller = new AbortController();
    abortControllersRef.current.add(controller);

    setQueryResult((prev) => ({ ...prev, loading: true, called: true }));

    try {
      const result = await client.query<T>(query, controller.signal);
      setQueryResult({ data: result, loading: false, error: null, called: true });
    } catch (error: any) {
      setQueryResult((prev) => ({ ...prev, error, loading: false }));
    } finally {
      abortControllersRef.current.delete(controller);
    }
  }, [query]);

  return [executeQuery, queryResult];
}
