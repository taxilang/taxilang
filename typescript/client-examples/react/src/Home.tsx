import classes from './Home.module.css';

export const Home = () => {
  return (
    <div className={classes.home}>
      <h2>TaxiQL React Examples</h2>
      <div>This repository contains a series of React-based examples demonstrating how to use the TaxiQL TypeScript SDK
        to
        execute queries against a Taxi workspace. The examples leverage Nebula to provide a test ecosystem, which
        includes:
      <ul>
        <li>A database containing film data.</li>
        <li>HTTP APIs for retrieving film reviews, streaming provider information, and correlating film IDs.</li>
        <li>A Kafka broker publishing on a release queue every 5 seconds.</li>
      </ul>
      <h3>Ways to Execute a Query</h3>
      <p>
          The basic syntax of a TaxiQL query looks like this:
      </p>
      <pre className={classes.taxiCode}>
          // Find all the people<br/>
          find &#123; Person[] &#125;<br/>
        <br/>
          // Find all the people named Jim<br/>
          find &#123; Person[]( FirstName == 'Jim' ) &#125;<br/>
        <br/>
          // Find a stream of person events from somewhere<br/>
          stream &#123; PersonEvents &#125;
      </pre>
      <h3>Query types</h3>
        Use <code>find</code> for one-time queries that return a response, and <code>stream</code> for continuous data
        streams like event feeds or real-time updates.
      <p>Depending on which query you're using, they can be executed using different methods</p>
      <p>The <code>find </code> query can be executed in four different ways using the TaxiQL Typescript SDK.
          The <code>stream</code> query can only be executed using the <b>Execute Query as Event Stream</b> and <b>Execute
            Query as Promise-Based Event Stream</b> methods</p>
      <ol>
        <li><p><strong>Execute Query</strong>:</p>
          <ul>
            <li>Executes the query and returns the result immediately.</li>
            <li>Suitable for retrieving static data without streaming updates.</li>
          </ul>
        </li>
        {/*<li><p><strong>Execute Query as Promise</strong>:</p>
          <ul>
            <li>Executes the query and returns a promise that resolves to the result.</li>
            <li>Useful for integrating with asynchronous workflows using <code>async/await</code> syntax.</li>
          </ul>
        </li>*/}
        <li><p><strong>Execute Query as Event Stream</strong>:</p>
          <ul>
            <li>Executes the query and provides results via an event stream.</li>
            <li>Ideal for scenarios where updates need to be received in real time.</li>
            <li>Allows you to subscribe to updates and handle results as they arrive.</li>
          </ul>
        </li>
        {/*<li><p><strong>Execute Query as Promise-Based Event Stream</strong>:</p>
          <ul>
            <li>Executes the query and returns a promise that resolves to an event stream.</li>
            <li>Combines the benefits of promises with the capability to process streaming updates asynchronously.
            </li>
          </ul>
        </li>*/}
      </ol>
      </div>
      <p><i>Check out the examples above to get started! 🚀</i></p>
    </div>
  )
}
