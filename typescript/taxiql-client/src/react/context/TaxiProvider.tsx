import type * as ReactTypes from 'react';
import * as React from 'react';
import {TaxiClient} from '../../core';
import {getTaxiContext} from './TaxiContext';

export interface TaxiProviderProps<TCache> {
  client: TaxiClient<TCache>;
  children?: ReactTypes.ReactNode;
}

const TaxiContext = getTaxiContext();

export const TaxiProvider = <TCache, >({ client, children }: TaxiProviderProps<TCache>) => {

  const parentContext = React.useContext(TaxiContext);

  const context = React.useMemo(() => {
    return {
      ...parentContext,
      client: client || parentContext.client,
    };
  }, [client]);

  if (!context.client) {
    console.error('TaxiProvider was not passed a client instance. Make sure you pass in your client via the "client" prop.')
  }

  return (
    <TaxiContext.Provider value={context}>{children}</TaxiContext.Provider>
  );
};
