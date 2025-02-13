import {Dispatch, SetStateAction, useEffect, useState} from 'react';
import {TaxiClient, TransportMethod} from '../../core';
import {QueryResult, TypedStream} from '../../types';
import {useTaxiClient} from './useTaxiClient';

// TODO: these could be useful in the query as well...
export interface SubscriptionCallbacks<T> {
  onData?: (data: T) => any,
  onError?: (error: Error) => void,
  onComplete?: () => void
}

/**
 * A hook for executing TaxiQL subscriptions in a React application.
 *
 * To use a subscription within a React component, call `useSubscription` and pass it TaxiQL.
 * It will return an object containing `loading`, `error`, and `data` properties.
 */

export function useSubscription<T>(
  query: TypedStream<T>,
  callbacks?: SubscriptionCallbacks<T>
): QueryResult<T> {
  const client = useTaxiClient();

  const [queryResult, setQueryResult] = useState<QueryResult<T>>({
    data: null,
    loading: true,
    error: null,
    called: false,
  });

  useEffect(() => {
    if (!query) return;

    const subscription = internalUseSubscription(
      query,
      'WS',
      client,
      setQueryResult,
      callbacks
    )

    // Cleanup function to unsubscribe when the component unmounts
    return () => {
      subscription?.unsubscribe();
    };
  }, [query]);

  return queryResult;
}

export function internalUseSubscription<T>(
  query: TypedStream<T>,
  transportMethod: TransportMethod,
  client: TaxiClient,
  setQueryResult: Dispatch<SetStateAction<QueryResult<T>>>,
  callbacks?: SubscriptionCallbacks<T>
): { unsubscribe: () => void } {
  setQueryResult((prev) => ({ ...prev, loading: true, called: true }));

  // Create subscription
  const subscription = client.subscribe<T>(
    query,
    {
      next: (newData: T) => {
        callbacks?.onData?.(newData)
        setQueryResult((prev) => {
          return {
            loading: false,
            error: null,
            called: true,
            data: newData,
          };
        });
      },
      error: (error: any) => {
        setQueryResult((prev) => ({ ...prev, error: error.message ?? error, loading: false }));
        callbacks?.onError?.(error)
      },
      complete: () => {
        setQueryResult((prev) => ({ ...prev, loading: false }));
        callbacks?.onComplete?.()
      },
      _unsubscribe: () => {
        setQueryResult((prev) => ({ ...prev, loading: false, called: false /* Does this make sense...? */}));
      }
    },
    transportMethod
  );
  return subscription
}
