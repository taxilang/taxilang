import React from 'react';
import {TaxiClient} from '../../core';
import {getTaxiContext} from '../context';

export function useTaxiClient(
  override?: TaxiClient<object>
): TaxiClient<object> {
  const context = React.useContext(getTaxiContext());
  const client = override || context.client;
  if (!client) {
    console.log(`
      Could not find "client" in the context or passed in as an option.
      Wrap the root component in an <TaxiProvider>, or pass an TaxiClient
      instance in via options.
    `)
  }

  return client!;
}
