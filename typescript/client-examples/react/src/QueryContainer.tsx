import {TypedQuery} from '@orbitalhq/taxiql-client';
import {ComponentProps} from 'react';
import Markdown from 'react-markdown';
import {ExecuteQuery} from './ExecuteQuery.tsx';
import classes from './QueryContainer.module.css';

type QueriesContainerProps<TResultType> = {
  query: TypedQuery<TResultType>
  readme: string
  executeQuery?: () => void
  streamQuery?: () => void
} & ComponentProps<"div">;
export const QueryContainer = ({...props}: QueriesContainerProps<unknown>) => {
  return (
    <div className={classes.queriesContainer}>
      <div className={classes.query}>
        <fieldset className={classes.taxiCode}>
          <legend>TaxiQL</legend>
          <div className={classes.taxiCodeContent}>{props.query.taxiQl}</div>
        </fieldset>
        <div className={classes.buttonContainer}>
          {props.executeQuery && <ExecuteQuery label="Execute query" onClick={() => props.executeQuery?.()}/>}
          {props.streamQuery && <ExecuteQuery label="Stream Query" onClick={() => props.streamQuery?.()}/>}
        </div>
        <Markdown className={classes.readme}>{props.readme}</Markdown>
      </div>
    </div>
  )
}
