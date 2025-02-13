import {useCallback, useRef, useState} from 'react';
import {QueryResult, TypedStream} from '../../types';
import {internalUseSubscription, SubscriptionCallbacks} from './useSubscription';
import {useTaxiClient} from './useTaxiClient';

/**
 * A hook for imperatively executing TaxiQL streams in a React application, e.g. in response to user interaction.
 * @param query - The TypedQuery object containing the subscription query.
 * @param callbacks - An object containing onData, onError, and onComplete callbacks.
 * @returns A tuple containing a function to call to start the subscription, a QueryResults object and an unsubscribe callback to terminate the subscription.
 */

export function useLazySubscription<T>(
  query: TypedStream<T>,
  callbacks?: SubscriptionCallbacks<T>
): [() => Promise<void>, QueryResult<T>, (() => void) | undefined] {
  const client = useTaxiClient();
  const subscriptionRef = useRef<{unsubscribe: () => void} | undefined>(undefined);

  const [queryResult, setQueryResult] = useState<QueryResult<T>>({
    data: null,
    loading: false,
    error: null,
    called: false,
  });

  const startQueryStream = useCallback(async () => {
    subscriptionRef.current = internalUseSubscription(
      query,
      'WS',
      client,
      setQueryResult,
      callbacks
    )
  }, [query]);

  return [startQueryStream, queryResult, subscriptionRef.current?.unsubscribe];
}
