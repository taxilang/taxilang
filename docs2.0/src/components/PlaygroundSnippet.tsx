import React, { ReactNode, useEffect, useState } from 'react'
import { flushSync } from 'react-dom'
import { PlayIcon } from '@heroicons/react/20/solid'
import * as pako from 'pako'
import axios from 'axios'
import { XMarkIcon } from '@heroicons/react/24/solid'
import CodeMirror from '@uiw/react-codemirror'
import { taxi } from '@/utils/taxiCodeMirrorLanguage'
import { json } from '@codemirror/lang-json'
import { taxiDarkTheme } from '@/utils/taxiCodeMirrorTheme'
import { Prose } from '@/components/docs/Prose'
import ReactMarkdown from 'react-markdown'
import dynamic from 'next/dynamic'

// Dynamically import QueryPlanVisualization to avoid SSR issues
const QueryPlanVisualization = dynamic(
  () => import('@/components/query-plan/QueryPlanVisualization'),
  { ssr: false }
)

const PanelHeader: React.FC<{
  headerText: string;
  showCloseButton: boolean;
  onClose?: () => void;
}> = ({headerText, showCloseButton, onClose}) => (
  <div className='text-sm font-bold flex mb-2'>
    <span>{headerText}</span>
    <div className={'grow'}></div>
    {showCloseButton && onClose && (
      <button onClick={onClose} className={'font-medium flex align-middle text-sm'}>
        Close&nbsp;
        <XMarkIcon className={'w-[16px]'}></XMarkIcon>
      </button>
    )}
  </div>
);

// Compound components
const Description: React.FC<{ children: string }> = ({ children }) => (
  <Prose className="mb-4 text-[0.925rem]">
    <ReactMarkdown>{children}</ReactMarkdown>
  </Prose>
);

const Scenario: React.FC<{ children: StubQueryMessage }> = ({ children }) => null;

const PlaygroundSnippet: React.FC<StubQueryDisplayProps> = ({scenario, displayQuery, displaySchema, title = "Try it out", description, primaryRunButton = false, showQueryPlan = false, children}) => {
  // Extract description and scenario from children
  const extractedDescription = React.Children.toArray(children).find(
    (child) => React.isValidElement(child) && child.type === Description
  ) as React.ReactElement<{ children: string }> | undefined;

  const extractedScenario = React.Children.toArray(children).find(
    (child) => React.isValidElement(child) && child.type === Scenario
  ) as React.ReactElement<{ children: StubQueryMessage }> | undefined;

  // Use extracted scenario if available, otherwise fall back to prop
  const actualScenario = extractedScenario?.props.children || scenario || { schema: '', query: '' };

  const [query, setQuery] = useState<string>(actualScenario.query || '');
  const [schema, setSchema] = useState<string>(actualScenario.schema || '');
  const [queryResult, setQueryResult] = useState<string>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [showSchema, setShowSchema] = useState<boolean>(false);
  const [showQueryPlanState, setShowQueryPlanState] = useState<boolean>(showQueryPlan);
  const [queryPlanData, setQueryPlanData] = useState<any>(null);
  const [isLoadingQueryPlan, setIsLoadingQueryPlan] = useState<boolean>(false);

  // Determine if we're running on localhost
  const [isLocalhost, setIsLocalhost] = useState<boolean>(false);
  const [playgroundUrl, setPlaygroundUrl] = useState<string>("https://playground.taxilang.org");
  const [embeddingUrl, setEmbeddingUrl] = useState<string>("https://playground.taxilang.org");

  // Check if we're running on localhost when component mounts
  useEffect(() => {
    if (typeof window !== 'undefined') {
      const isLocal = window.location.hostname === 'localhost' ||
                    window.location.hostname === '127.0.0.1';
      setIsLocalhost(isLocal);

      // Set the playground URL based on environment
      const baseUrl = isLocal ? "http://localhost:9500" : "https://playground.taxilang.org";
      setPlaygroundUrl(baseUrl);
      setEmbeddingUrl(baseUrl);

      // Log which API we're using in development mode
      if (process.env.NODE_ENV === 'development') {
        console.log(`PlaygroundSnippet: Using Taxi Playground at ${baseUrl}`);
      }
    }
  }, []);

  const shouldDisplayQuery = displayQuery ?? true;
  // Schema display prop is ignored - we always show schema in an expandable panel

  useEffect(() => {
    const updatedMessage = {...actualScenario, query, schema};
    const newUrl = getEmbeddingUrl(updatedMessage);
    setEmbeddingUrl(newUrl);
  }, [query, schema, actualScenario, playgroundUrl]);

  // Parse query initially if showQueryPlan is true
  useEffect(() => {
    if (showQueryPlan && query && schema && playgroundUrl) {
      parseQuery();
    }
  }, [playgroundUrl]); // Run when playground URL is set

  // Parse query when query plan is shown or when query/schema changes
  useEffect(() => {
    if (showQueryPlanState && query && schema) {
      parseQuery();
    } else if (!showQueryPlanState) {
      setQueryPlanData(null);
    }
  }, [showQueryPlanState, query, schema]);

  const getEmbeddingUrl = (query: StubQueryMessage): string => {
    const deflated = pako.gzip(JSON.stringify(query));
    const base64Encoded = btoa(String.fromCharCode.apply(null, deflated))
    return `${playgroundUrl}/#pako:${base64Encoded}`
  }

  const handleQueryChange = (value: string) => {
    setQuery(value);
  };

  const handleSchemaChange = (value: string) => {
    setSchema(value);
  };

  const parseQuery = async () => {
    if (!query || !schema) {
      return;
    }

    setIsLoadingQueryPlan(true);
    const updatedMessage = {...actualScenario, query, schema};

    try {
      const endpoint = `${playgroundUrl}/api/query/parse`;
      const response = await axios.post(endpoint, updatedMessage);

      if (response.data?.queryPlan?.diagramData) {
        setQueryPlanData(response.data.queryPlan.diagramData);
      }
    } catch (err) {
      console.error('Error parsing query:', err);
      setQueryPlanData(null);
    } finally {
      setIsLoadingQueryPlan(false);
    }
  };

  const submitQuery = async () => {
    setIsLoading(true);
    setError(null);
    setQueryResult(null)
    const updatedMessage = {...actualScenario, query, schema};

    try {
      const endpoint = `${playgroundUrl}/api/query`;
      console.log(`Submitting query to: ${endpoint}`);
      const response = await axios.post(endpoint, updatedMessage);
      console.log('Server response:', response.data);
      setQueryResult(JSON.stringify(response.data, null, 2));
    } catch (err) {
      const errDetails = err.response?.data?.message ?? err.message;
      setError(`An error occurred while running the query:
${errDetails}`);
      console.error('Error running query:', err);
    } finally {
      setIsLoading(false);
    }
  };

  // CodeMirror theme settings
  const codeMirrorTheme = taxiDarkTheme();
  const editorHeight = "150px";

  return (
    <div className="playground-snippet-container bg-slate-800/60 rounded-lg p-4 text-slate-300 not-prose">
      {/* Header with title and controls */}
      <div className="flex justify-between items-center mb-4">
        <h3 className="text-md text-slate-200">{title}</h3>
        <div className="flex items-center gap-3">
          <button
            className='text-sky-400 hover:text-sky-300 border border-sky-400 hover:border-sky-300 px-2 py-1 text-xs rounded font-medium'
            onClick={() => setShowSchema(!showSchema)}
          >
            {showSchema ? 'Hide Schema' : 'Show Schema'}
          </button>
          <button
            className='text-sky-400 hover:text-sky-300 border border-sky-400 hover:border-sky-300 px-2 py-1 text-xs rounded font-medium'
            onClick={() => setShowQueryPlanState(!showQueryPlanState)}
          >
            {showQueryPlanState ? 'Hide Query Plan' : 'Show Query Plan'}
          </button>
          <button
            className={primaryRunButton
              ? 'flex items-center bg-sky-500 hover:bg-sky-300 text-slate-900 font-semibold text-sm px-3 py-1 rounded-md transition-colors'
              : 'flex items-center text-sky-400 hover:text-sky-300 text-sm'}
            onClick={submitQuery}
            disabled={isLoading}
          >
            <span className='mr-1'>{isLoading ? 'Running...' : 'Run'}</span>
            <PlayIcon className={primaryRunButton ? 'w-[16px] text-slate-900' : 'w-[16px]'}></PlayIcon>
          </button>
        </div>
      </div>

      {/* Description Section */}
      {(extractedDescription || description) && (
        extractedDescription ? extractedDescription : (
          <Prose className="mb-4 text-[0.925rem]">
            <div dangerouslySetInnerHTML={{ __html: description }} />
          </Prose>
        )
      )}

      {/* Schema Section */}
      <div className={`overflow-hidden transition-all duration-300 ease-in-out ${
        showSchema ? 'max-h-96 opacity-100 mb-4' : 'max-h-0 opacity-0 mb-0'
      }`}>
        <PanelHeader headerText="Schema" showCloseButton={false}/>
        <div className="rounded-md overflow-hidden cm-wrapper">
          <CodeMirror
            value={schema}
            onChange={handleSchemaChange}
            theme={codeMirrorTheme}
            height={editorHeight}
            placeholder="Enter schema here"
            extensions={[taxi()]}
            basicSetup={{
              lineNumbers: false,
              highlightActiveLine: false,
              highlightSelectionMatches: true,
              autocompletion: true,
            }}
          />
        </div>
      </div>

      {/* Query Section */}
      {shouldDisplayQuery && (
        <div className="rounded-md overflow-hidden cm-wrapper">
          <CodeMirror
            value={query}
            onChange={handleQueryChange}
            theme={codeMirrorTheme}
            height="auto"
            placeholder="Enter query here"
            extensions={[taxi()]}
            basicSetup={{
              lineNumbers: false,
              highlightActiveLine: false,
              highlightSelectionMatches: true,
              autocompletion: true,
            }}
          />
        </div>
      )}

      {/* Query Plan Section */}
      <div className={`overflow-hidden transition-all duration-300 ease-in-out ${
        showQueryPlanState ? 'max-h-[600px] opacity-100 my-4' : 'max-h-0 opacity-0 mb-0'
      }`}>
        <PanelHeader headerText="Query Plan" showCloseButton={false}/>
        {isLoadingQueryPlan ? (
          <div className="flex items-center justify-center py-8 text-slate-400">
            <span>Loading query plan...</span>
          </div>
        ) : queryPlanData ? (
          <div className="rounded-md overflow-hidden">
            <QueryPlanVisualization queryPlanData={queryPlanData} height={500} />
          </div>
        ) : showQueryPlanState ? (
          <div className="flex items-center justify-center py-8 text-slate-400">
            <span>No query plan available. Make sure both query and schema are provided.</span>
          </div>
        ) : null}
      </div>

      {/* Controls */}
      <div className='flex justify-between text-xs text-slate-300 mt-3'>
        {/* Localhost indicator - only shown in development mode */}
        {process.env.NODE_ENV === 'development' && (
          <div className={`${isLocalhost ? 'text-green-400' : 'text-orange-400'} font-mono text-xs`}>
            {isLocalhost ? 'Using localhost:9500' : 'Using production API'}
          </div>
        )}

        <div className='flex items-center text-xs'>
          <span>Play with this snippet by editing it here, or&nbsp;</span>
          <a className='underline hover:text-sky-600' href={embeddingUrl} target='_blank'>edit it on Taxi
            Playground</a>
        </div>
      </div>

      {/* Results Section */}
      <div className={`overflow-hidden transition-all duration-300 ease-in-out ${
        queryResult ? 'max-h-96 opacity-100 mt-4' : 'max-h-0 opacity-0 mt-0'
      }`}>
        <div className={'flex flex-col border-t border-t-slate-400 pt-4'}>
          <PanelHeader
            headerText="Result"
            showCloseButton={true}
            onClose={() => {
              flushSync(() => {
                setQueryResult(null);
              });
            }}
          />
          <div className="rounded-md overflow-hidden cm-wrapper">
            <CodeMirror
              value={queryResult || ''}
              theme={codeMirrorTheme}
              readOnly={true}
              height="auto"
              extensions={[json()]}
              basicSetup={{
                lineNumbers: true,
                highlightActiveLine: false,
                highlightSelectionMatches: false,
                autocompletion: false,
                foldGutter: true,
              }}
            />
          </div>
        </div>
      </div>

      {/* Error Section */}
      <div className={`overflow-hidden transition-all duration-300 ease-in-out ${
        error ? 'max-h-96 opacity-100 mt-4' : 'max-h-0 opacity-0 mt-0'
      }`}>
        <div className={'flex flex-col border-t border-t-slate-400 pt-4'}>
          <PanelHeader
            headerText="Query failed"
            showCloseButton={true}
            onClose={() => {
              flushSync(() => {
                setError(null);
              });
            }}
          />
          <div className="rounded-md overflow-hidden cm-wrapper">
            <CodeMirror
              value={error || ''}
              theme={codeMirrorTheme}
              readOnly={true}
              height="auto"
              basicSetup={{
                lineNumbers: false,
                highlightActiveLine: false,
                highlightSelectionMatches: false,
                autocompletion: false,
              }}
            />
          </div>
        </div>
      </div>
    </div>
  );
};

export interface StubQueryDisplayProps {
  scenario?: StubQueryMessage;
  displayQuery?: boolean
  displaySchema?: boolean
  title?: string
  description?: string
  primaryRunButton?: boolean
  showQueryPlan?: boolean
  children?: ReactNode
}

export interface StubQueryMessage {
  schema: string;
  query?: string;
  parameters?: { [index: string]: any };
  stubs?: OperationStub[];
  expectedJson?: string | null;
}

export interface OperationStub {
  operationName: string;
  response: string;
}

// Attach compound components
(PlaygroundSnippet as any).Description = Description;
(PlaygroundSnippet as any).Scenario = Scenario;

export default PlaygroundSnippet;
