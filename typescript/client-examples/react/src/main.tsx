import {TaxiClient, TaxiProvider} from '@orbitalhq/taxiql-client';
import {StrictMode} from 'react'
import {createRoot} from 'react-dom/client'
import './index.css'
import {BrowserRouter, Navigate, Route, Routes} from 'react-router';
import App from './App.tsx'
import {QueryExample1} from './examples/QueryExample1.tsx';
import {QueryExample2} from './examples/QueryExample2.tsx';
import {QueryExample3} from './examples/QueryExample3.tsx';
import {QueryExample4} from './examples/QueryExample4.tsx';
import {QueryExample5} from './examples/QueryExample5.tsx';
import {QueryExample6} from './examples/QueryExample6.tsx';
import {Home} from './Home.tsx';
import HooksShowcase from './HooksShowcase.tsx';

type example = {
  to: string
  label: string
  Component: React.ComponentType
}

export const examples: example[] = [
  { to: '/', label: 'Home', Component: Home },
  { to: '/example1', label: 'Example 1', Component: QueryExample1 },
  { to: '/example2', label: 'Example 2', Component: QueryExample2 },
  { to: '/example3', label: 'Example 3', Component: QueryExample3 },
  { to: '/example4', label: 'Example 4', Component: QueryExample4 },
  { to: '/example5', label: 'Example 5', Component: QueryExample5 },
  { to: '/example6', label: 'Example 6', Component: QueryExample6 },
]

const client = new TaxiClient({
  orbitalServerUrl: 'http://localhost:5173/api',
  wsOrbitalServerUrl: 'http://localhost:9022'
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <TaxiProvider client={client}>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<App/>}>
            {examples.map(({ to, Component }, index) => (
              <Route key={index} path={to} element={<Component/>}/>
            ))}
            <Route path="/hooks" element={<HooksShowcase/>}/>
            <Route
              path="*"
              element={<Navigate to="/" replace/>}
            />
          </Route>
        </Routes>
      </BrowserRouter>
    </TaxiProvider>
  </StrictMode>,
)
