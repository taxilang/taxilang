import type * as ReactTypes from 'react';
import React from 'react';
import {TaxiClient} from '../../core';
import {canUseSymbol} from '../../utils';

export interface TaxiContextValue {
  client?: TaxiClient<object>;
}

// Global storage for the context
const contextKey = canUseSymbol ? Symbol.for('__TAXI_CONTEXT__') : '__TAXI_CONTEXT__';

// Create a global store to prevent multiple contexts
const globalContextStore = (globalThis as any)[contextKey] || ((globalThis as any)[contextKey] = {});

export function getTaxiContext(): ReactTypes.Context<TaxiContextValue> {
  if (!('createContext' in React)) {
    console.error(`
         Invoking 'getTaxiContext' in an environment where 'React.createContext' is not available.\n
         The Taxi Client functionality you are trying to use is only available in React Client Components.\n
         Please make sure to add "use client" at the top of your file.\n
         For more information, see https://nextjs.org/docs/getting-started/react-essentials#client-components
      `);
  }

  if (!globalContextStore.context) {
    globalContextStore.context = React.createContext<TaxiContextValue>({});
    globalContextStore.context.displayName = 'TaxiContext';
  }

  return globalContextStore.context;
}
