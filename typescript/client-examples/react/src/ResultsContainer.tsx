import {ComponentProps} from 'react';
import loadingSpinner from './assets/spinner.png'
import classes from './ResultsContainer.module.css';

type ContainerProps = {
  items: any[],
  loading: boolean,
  error: Error | null,
  called?: boolean,
  closeStream?: () => void,
}  & ComponentProps<"div">;
export const ResultsContainer = ({...props}: ContainerProps) => {
  return (
    <div className={classes.queryResults}>
      <div className={classes.resultsHeader}>
        {(props.loading || !!props.items?.length || props.error) && <h3>Query results</h3>}
        {props.loading &&
          <div className={classes.loading}><img src={loadingSpinner} className={classes.loadingSpinner}/><span>Loading...</span>
          </div>}
        {props.called && props.closeStream && <button onClick={() => props.closeStream?.()}>Close stream</button>}
        {props.error && <div className={classes.error}>{props.error.message}</div>}
      </div>
      {!!props.items?.length &&
        <div className={classes.queryResultsTableWrapper}>
          <table>
            <thead>
              <tr>
                {Object.keys(props.items[0]).map((key, index) => (
                  <th key={index}>{key}</th>
                ))
                }
              </tr>
            </thead>
            <tbody>
              {props.items.map((item, index) => (
                <tr key={index}>
                  {Object.keys(item).map((key, index) => (
                    <td key={index}>
                      {renderItemValue(item[key])}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      }
    </div>
  )
}

const renderItemValue = (value: any) => {
  if (typeof value === "object" && value !== null) {
    return (
      <div>
        {Object.keys(value).map((nestedKey, nestedIndex) => (
          <div key={nestedIndex}>
            <strong>{nestedKey}:</strong> {renderItemValue(value[nestedKey])}
          </div>
        ))}
      </div>
    );
  }
  return value;
};
