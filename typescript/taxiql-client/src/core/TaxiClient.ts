import {nanoid} from 'nanoid';
import {TypedQuery} from '../types';

export interface TaxiClientConfig {
  orbitalServerUrl?: string,
  wsOrbitalServerUrl?: string // WebSocket URL
}

type QueryHandlers<T> = {
  next: (data: T) => void // Callback for when new data is received
  error?: (err: any) => void // Optional callback for errors
  complete?: () => void // Optional callback when the subscription ends
  _unsubscribe?: () => void // Optional internal callback when the stream is unsubscribed from
};


export type TransportMethod = 'WS' | 'SSE' | 'HTTP_MULTIPART'

// TODO: TCacheShape, and a caching mechanism still needs to be implemented
//       (see the ApolloClient for prior art)
export class TaxiClient<TCacheShape = null> {
  private readonly orbitalServerUrl?: string;
  private readonly wsOrbitalServerUrl?: string;
  private readonly eventSources: Map<string, EventSource> = new Map();
  // private cache: any;

  constructor(config: TaxiClientConfig) {
    this.orbitalServerUrl = config.orbitalServerUrl;
    this.wsOrbitalServerUrl = config.wsOrbitalServerUrl;
    // this.cache = cache
  }

  /**
   * Executes a typed query and returns the result as a promise.
   *
   * @template T - The expected response type of the query.
   *
   * @param query - The `TypedQuery<T>` instance representing the query to execute.
   *
   * @returns A promise that resolves with the query result of type `T`.
   */
  async query<T>(query: TypedQuery<T>, signal?: AbortSignal): Promise<T> {
    return await this.executeTaxiQlQuery(query, signal)
  }

  /**
   * Subscribes to a typed query and handles streaming data updates.
   *
   * @template T - The expected response type of the query.
   *
   * @param query - The `TypedQuery<T>` instance representing the query to execute.
   * @param handlers - An object containing callback functions to handle different events:
   *   - `next` - Called when new data of type `T` is received.
   *   - `error` (optional) - Called when an error occurs.
   *   - `complete` (optional) - Called when the stream is completed.
   *   - `_unsubscribe` (optional) - Internal callback executed on unsubscription.
   * @param transportMethod - The transport method to use, defaulting to `'WS'` (WebSocket).
   *
   * @returns An object with an `unsubscribe` method to stop receiving updates.
   */
  subscribe<T>(
    query: TypedQuery<T>,
    handlers: {
      next: (data: T) => void
      error?: (err: any) => void
      complete?: () => void
      _unsubscribe?: () => void
    },
    transportMethod: TransportMethod = 'WS'
  ): { unsubscribe: () => void } {
    return this.streamTaxiQlQuery<T>(query, handlers, transportMethod)
  }

  /**
   * One time execution of queries for 'find'
   */
  private async executeTaxiQlQuery<T>(query: TypedQuery<T>, signal?: AbortSignal): Promise<T> {
    if (query === undefined) {
      throw Error('No query match found, make sure you have the codegen script running.')
    }
    if (!this.orbitalServerUrl) {
      throw new Error(`You need to provide a 'orbitalServerUrl' in your TaxiClient config`)
    }
    const clientId = nanoid();

    const url = new URL(`${this.orbitalServerUrl}/api/taxiql?clientQueryId=${clientId}`);

    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: query.taxiQl,
      signal
    });

    let result: T | Error
    try {
      result = await response.json();
    } catch (e: any) {
      throw Error(e)
    }

    if (response.status !== 200) {
      throw Error((result as Error).message)
    }

    return result as T;
  }

  /**
   * Initiates a subscription to a TaxiQL query.
   * Should be able to be used for both 'find' and 'stream' queries
   */
  private streamTaxiQlQuery<T>(
    query: TypedQuery<T>,
    handlers: QueryHandlers<T>,
    transportMethod: TransportMethod
  ): { unsubscribe: () => void } {
    switch (transportMethod) {
      case 'WS':
        return this.streamWithWebSocket<T>(query, handlers);
      case 'SSE':
        return this.streamWithSSE<T>(query, handlers);
      case 'HTTP_MULTIPART':
        return this.streamWithHTTPMultipart<T>(query, handlers)
    }
  }

  /**
   * Stream using Server-Sent Events (SSE).
   * @param query - The TypedQuery object containing the subscription query.
   * @param handlers - An object containing next, error, complete and _unsubscribe callbacks.
   * @returns An object with an unsubscribe method to terminate the subscription.
   */
  private streamWithSSE<T>(
    query: TypedQuery<T>,
    handlers: QueryHandlers<T>
  ): { unsubscribe: () => void } {
    const clientQueryId = nanoid();  // Generate a unique ID for this subscription
    const sseUrl = `${this.orbitalServerUrl}/api/taxiql?clientQueryId=${clientQueryId}&query=${encodeURIComponent(query.taxiQl)}`;

    // Check if we already have an EventSource for this clientQueryId
    if (!this.eventSources.has(clientQueryId)) {
      const eventSource = new EventSource(sseUrl);

      eventSource.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          handlers.next(data);
        } catch (err) {
          console.error("Error parsing SSE data:", err);
          handlers.error?.(err);
        }
      };

      eventSource.onerror = (error) => {
        // TODO: how do we actually do error handling here (other than a connection error, which is already a hack)??
        if (eventSource.readyState !== EventSource.OPEN) {
          handlers.error?.(new Error('A connection error occurred'))
        } else {
          // Note: We're now sending errors down as an error message, so
          // assume that all onerror signals are just completion.
          console.log(
            'Received error event  - treating this as a close signal',
          );
          handlers.complete?.();
        }
      };

      eventSource.onopen = () => {
        console.log("SSE connected to Orbital server");
      };

      // Store the eventSource instance in the map with clientQueryId as the key
      this.eventSources.set(clientQueryId, eventSource);
    }

    return {
      unsubscribe: () => {
        const eventSource = this.eventSources.get(clientQueryId);
        if (eventSource) {
          eventSource.close();
          console.log("SSE disconnected from Orbital server");
          this.eventSources.delete(clientQueryId);  // Remove it from the map
          handlers._unsubscribe?.()
        }
      },
    };
  }

  /**
   * Stream using WebSockets.
   * @param query - The TypedQuery object containing the subscription query.
   * @param handlers - An object containing next, error, complete and _unsubscribe callbacks.
   * @returns An object with an unsubscribe method to terminate the subscription.
   */
  private streamWithWebSocket<T>(
    query: TypedQuery<T>,
    handlers: QueryHandlers<T>
  ): { unsubscribe: () => void } {
    if (!this.wsOrbitalServerUrl) {
      throw new Error(`You need to provide a 'wsOrbitalServerUrl' in your TaxiClient config`)
    }

    const websocket = new WebSocket(`ws://localhost:9022/api/query/taxiql`);
    const clientQueryId = nanoid();

    websocket.onopen = () => {
      console.log(`WebSocket connected for query ${clientQueryId}`);
      websocket.send(JSON.stringify({ query: query.taxiQl, clientQueryId }));
    };

    websocket.onmessage = (event) => {
      try {
        const { value } = JSON.parse(event.data);
        handlers.next(value);
      } catch (err) {
        console.error(`Error parsing WebSocket data for query ${clientQueryId}:`, err);
        handlers.error?.(err);
      }
    };

    websocket.onerror = (error) => {
      console.error(`WebSocket error for query ${clientQueryId}:`, error);
      handlers.error?.(error);
    };

    websocket.onclose = () => {
      console.log(`WebSocket closed for query ${clientQueryId}`);
      handlers.complete?.();
    };

    return {
      unsubscribe: () => {
        console.log(`Unsubscribing WebSocket for query ${clientQueryId}`);
        websocket.close();
        handlers._unsubscribe?.();
      },
    };
  }

  // TODO: get the server side implementation of HTTP multipart working,
  //       and test with the below rudimentary implementation
  /**
   * Subscribes to a TaxiQL query using HTTP multipart responses.
   * This allows receiving real-time updates using Orbital incremental delivery over HTTP.
   * @param query - The TypedQuery object containing the subscription query.
   * @param handlers - An object containing next, error, complete and _unsubscribe callbacks.
   * @returns An object with an unsubscribe method to terminate the subscription.
   */
  streamWithHTTPMultipart<T>(
    query: TypedQuery<T>,
    handlers: QueryHandlers<T>
  ): { unsubscribe: () => void } {
    // Create an AbortController to allow stopping the request when unsubscribing
    const controller = new AbortController();
    const { signal } = controller;

    if (!this.orbitalServerUrl) {
      throw new Error(`You need to provide a 'orbitalServerUrl' in your TaxiClient config`)
    }

    fetch(this.orbitalServerUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'multipart/mixed', // Request a multipart response from the server
      },
      body: JSON.stringify({ query: query.taxiQl }), // Send the subscription query
      signal, // Attach the abort signal to allow cancellation
    })
      .then(async (response) => {
        if (!response.ok || !response.body) {
          throw new Error(`HTTP error! Status: ${response.status}`);
        }

        // Create a reader to process the response stream
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = ''; // Buffer to accumulate chunks of the response

        while (true) {
          const { done, value } = await reader.read();
          if (done) break; // Stop reading when the stream is complete

          buffer += decoder.decode(value, { stream: true }); // Decode received chunks

          let boundaryIndex;
          // Process the response in chunks based on multipart boundaries
          while ((boundaryIndex = buffer.indexOf('\r\n--')) !== -1) {
            const part = buffer.slice(0, boundaryIndex);
            buffer = buffer.slice(boundaryIndex + 2); // Remove processed part from buffer

            if (part.trim()) {
              try {
                // Extract JSON content from the multipart response
                const json = part.split('\r\n\r\n')[1].trim();
                const result = JSON.parse(json);

                // Call the `next` handler with the new data
                handlers.next(result.data);
              } catch (err) {
                // Call the `error` handler if JSON parsing fails
                handlers.error?.(err);
              }
            }
          }
        }

        // Call the `complete` handler when the response stream ends
        handlers.complete?.();
      })
      .catch((err) => {
        // Ignore abort errors (these occur when the user unsubscribes)
        if (err.name !== 'AbortError') {
          handlers.error?.(err);
        }
      });

    return {
      /**
       * Unsubscribes from the subscription by aborting the HTTP request.
       * This stops further responses from being processed.
       */
      unsubscribe: () => {
        controller.abort()
        handlers._unsubscribe?.()
      },
    };
  }
}
