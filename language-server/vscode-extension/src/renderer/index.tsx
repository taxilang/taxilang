/**
 * TaxiQL Notebook Renderer - React Implementation
 *
 * This renderer handles custom MIME types for TaxiQL notebook outputs:
 * - application/vnd.taxi.results+json: Execution results with table/JSON views
 * - Tabbed interface with Data and Profiler tabs
 */

import React, { useState, useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { AgGridReact } from 'ag-grid-react';
import { ModuleRegistry, AllCommunityModule, ColDef } from 'ag-grid-community';
import { JSONTree } from 'react-json-tree';
import QueryPlanVisualization from '@query-plan/QueryPlanVisualization';
import type { QueryPlanDiagramData } from '@query-plan/types';

// Import CSS as strings for manual injection
import agGridCss from 'ag-grid-community/styles/ag-grid.css';
import agThemeAlpineCss from 'ag-grid-community/styles/ag-theme-alpine.css';

// Import codicon CSS and font
import codiconCssRaw from '@vscode/codicons/dist/codicon.css';
import codiconFont from '@vscode/codicons/dist/codicon.ttf';

// Import React Flow CSS
import reactFlowCss from '@xyflow/react/dist/style.css';

// Register AG-Grid modules
ModuleRegistry.registerModules([AllCommunityModule]);

// Inject CSS into the document
const injectCSS = (css: string, id: string) => {
   if (!document.getElementById(id)) {
      const style = document.createElement('style');
      style.id = id;
      style.textContent = css;
      document.head.appendChild(style);
   }
};

// Custom dark theme CSS for AG-Grid that uses VSCode variables
const darkThemeOverrides = `
   .ag-theme-alpine-dark {
      --ag-background-color: var(--vscode-editor-background);
      --ag-foreground-color: var(--vscode-editor-foreground);
      --ag-header-background-color: var(--vscode-sideBar-background);
      --ag-header-foreground-color: var(--vscode-sideBar-foreground);
      --ag-odd-row-background-color: var(--vscode-editor-background);
      --ag-row-hover-color: var(--vscode-list-hoverBackground);
      --ag-selected-row-background-color: var(--vscode-list-activeSelectionBackground);
      --ag-border-color: var(--vscode-panel-border);
      --ag-row-border-color: var(--vscode-panel-border);
      --ag-header-column-separator-color: var(--vscode-panel-border);
      --ag-font-family: var(--vscode-font-family);
      --ag-font-size: var(--vscode-font-size);
   }

   .ag-theme-alpine {
      --ag-background-color: var(--vscode-editor-background);
      --ag-foreground-color: var(--vscode-editor-foreground);
      --ag-header-background-color: var(--vscode-sideBar-background);
      --ag-header-foreground-color: var(--vscode-sideBar-foreground);
      --ag-odd-row-background-color: var(--vscode-editor-background);
      --ag-row-hover-color: var(--vscode-list-hoverBackground);
      --ag-selected-row-background-color: var(--vscode-list-activeSelectionBackground);
      --ag-border-color: var(--vscode-panel-border);
      --ag-row-border-color: var(--vscode-panel-border);
      --ag-header-column-separator-color: var(--vscode-panel-border);
      --ag-font-family: var(--vscode-font-family);
      --ag-font-size: var(--vscode-font-size);
   }
`;

// Fix codicon CSS to use the embedded font
// Replace all relative font URLs with the data URL (including query parameters)
const codiconCss = codiconCssRaw.replace(
   /url\(['"]?\.\/codicon\.ttf[^'")\s]*['"]?\)/g,
   `url('${codiconFont}')`
);

// Inject AG-Grid styles
injectCSS(agGridCss, 'ag-grid-css');
injectCSS(agThemeAlpineCss, 'ag-theme-alpine-css');
injectCSS(darkThemeOverrides, 'ag-grid-vscode-theme');
injectCSS(codiconCss, 'codicon-css');

// Inject React Flow styles
injectCSS(reactFlowCss, 'react-flow-css');

type RendererContext = any;

interface OutputItem {
   json(): any;
   mime: string;
}

// VSCode theme detection
const getVSCodeTheme = (): 'dark' | 'light' => {
   const body = document.body;
   const theme = body.getAttribute('data-vscode-theme-kind');
   return theme === 'vscode-dark' || theme === 'vscode-high-contrast' ? 'dark' : 'light';
};

// JSON Tree theme for VSCode
const getJSONTreeTheme = (isDark: boolean) => ({
   scheme: 'vscode',
   author: 'microsoft',
   base00: isDark ? '#1e1e1e' : '#ffffff',
   base01: isDark ? '#252526' : '#f3f3f3',
   base02: isDark ? '#2d2d30' : '#e8e8e8',
   base03: isDark ? '#3e3e42' : '#d4d4d4',
   base04: isDark ? '#6a6a6a' : '#969696',
   base05: isDark ? '#cccccc' : '#333333',
   base06: isDark ? '#e0e0e0' : '#1a1a1a',
   base07: isDark ? '#ffffff' : '#000000',
   base08: isDark ? '#f48771' : '#cd3131',
   base09: isDark ? '#ce9178' : '#cd8028',
   base0A: isDark ? '#dcdcaa' : '#007acc',
   base0B: isDark ? '#ce9178' : '#0d8f28',
   base0C: isDark ? '#4ec9b0' : '#00979c',
   base0D: isDark ? '#569cd6' : '#0451a9',
   base0E: isDark ? '#c586c0' : '#a31515',
   base0F: isDark ? '#d16969' : '#cd3131',
});

interface DataViewerProps {
   data: any;
   contentType: string;
}

const DataViewer: React.FC<DataViewerProps> = ({ data, contentType }) => {
   const [viewMode, setViewMode] = useState<'table' | 'json'>('table');
   const [theme, setTheme] = useState<'dark' | 'light'>(getVSCodeTheme());

   useEffect(() => {
      // Initial theme detection
      const detectedTheme = getVSCodeTheme();
      console.log('Initial theme detected:', detectedTheme);
      console.log('Body theme attribute:', document.body.getAttribute('data-vscode-theme-kind'));
      console.log('Body class:', document.body.className);
      setTheme(detectedTheme);

      // Watch for theme changes
      const observer = new MutationObserver(() => {
         const newTheme = getVSCodeTheme();
         console.log('Theme changed to:', newTheme);
         setTheme(newTheme);
      });

      observer.observe(document.body, {
         attributes: true,
         attributeFilter: ['data-vscode-theme-kind', 'class'],
      });

      return () => observer.disconnect();
   }, []);

   const isDark = theme === 'dark';
   console.log('Current theme state:', theme, 'isDark:', isDark);

   // Normalize data to always be an array for table view
   const normalizeData = () => {
      if (data === null || data === undefined) {
         return [];
      }
      if (Array.isArray(data)) {
         return data;
      }
      // Check if it's an object (but not null)
      if (typeof data === 'object' && data !== null) {
         // Object - wrap in array to show its properties as columns
         return [data];
      }
      // Scalar value (string, number, boolean) - wrap in object with 'value' field
      return [{ value: data }];
   };

   const isScalar = !Array.isArray(data) && (typeof data !== 'object' || data === null);
   const tableData = normalizeData();

   // Debug logging
   console.log('DataViewer - Raw data:', data);
   console.log('DataViewer - Normalized tableData:', tableData);
   console.log('DataViewer - isScalar:', isScalar);

   // Generate column definitions for AG-Grid
   const getColumnDefs = (): ColDef[] => {
      if (tableData.length === 0) {
         console.log('No table data, returning empty columns');
         return [];
      }

      const firstRow = tableData[0];
      console.log('First row for column generation:', firstRow);

      if (typeof firstRow !== 'object' || firstRow === null) {
         console.log('First row is not an object, using value column');
         return [{ field: 'value', headerName: 'Value', flex: 1 }];
      }

      const columns = Object.keys(firstRow).map((key) => ({
         field: key,
         headerName: key,
         flex: 1,
         sortable: true,
         filter: true,
         resizable: true,
      }));

      console.log('Generated columns:', columns);
      return columns;
   };

   const gridStyle = {
      height: '400px',
      width: '100%',
   };

   return (
      <div style={{
         fontFamily: 'var(--vscode-font-family)',
         color: 'var(--vscode-foreground)',
      }}>
         {/* View Mode Toggle */}
         <div style={{
            display: 'flex',
            gap: '4px',
            marginBottom: '12px',
            borderBottom: '1px solid var(--vscode-panel-border)',
            paddingBottom: '8px',
         }}>
            <button
               onClick={() => setViewMode('table')}
               style={{
                  padding: '6px 12px',
                  border: viewMode === 'table' ? '1px solid var(--vscode-button-border)' : '1px solid transparent',
                  background: viewMode === 'table' ? 'var(--vscode-button-background)' : 'transparent',
                  color: viewMode === 'table' ? 'var(--vscode-button-foreground)' : 'var(--vscode-foreground)',
                  cursor: 'pointer',
                  borderRadius: '2px',
                  fontSize: '13px',
                  fontWeight: '400',
                  fontFamily: 'var(--vscode-font-family)',
                  transition: 'background 0.1s ease',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
               }}
               onMouseEnter={(e) => {
                  if (viewMode !== 'table') {
                     (e.target as HTMLElement).style.background = 'var(--vscode-button-secondaryHoverBackground)';
                  }
               }}
               onMouseLeave={(e) => {
                  if (viewMode !== 'table') {
                     (e.target as HTMLElement).style.background = 'transparent';
                  }
               }}
            >
               <span className="codicon codicon-table"></span>
               Table
            </button>
            <button
               onClick={() => setViewMode('json')}
               style={{
                  padding: '6px 12px',
                  border: viewMode === 'json' ? '1px solid var(--vscode-button-border)' : '1px solid transparent',
                  background: viewMode === 'json' ? 'var(--vscode-button-background)' : 'transparent',
                  color: viewMode === 'json' ? 'var(--vscode-button-foreground)' : 'var(--vscode-foreground)',
                  cursor: 'pointer',
                  borderRadius: '2px',
                  fontSize: '13px',
                  fontWeight: '400',
                  fontFamily: 'var(--vscode-font-family)',
                  transition: 'background 0.1s ease',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
               }}
               onMouseEnter={(e) => {
                  if (viewMode !== 'json') {
                     (e.target as HTMLElement).style.background = 'var(--vscode-button-secondaryHoverBackground)';
                  }
               }}
               onMouseLeave={(e) => {
                  if (viewMode !== 'json') {
                     (e.target as HTMLElement).style.background = 'transparent';
                  }
               }}
            >
               <span className="codicon codicon-json"></span>
               JSON
            </button>
         </div>

         {/* Table View */}
         {viewMode === 'table' && (
            <>
               {tableData.length === 0 ? (
                  <div style={{
                     padding: '20px',
                     textAlign: 'center',
                     color: 'var(--vscode-descriptionForeground)',
                     background: 'var(--vscode-editor-background)',
                     border: '1px solid var(--vscode-panel-border)',
                     borderRadius: '4px',
                  }}>
                     No data to display
                  </div>
               ) : (
                  <div
                     className={isDark ? 'ag-theme-alpine-dark' : 'ag-theme-alpine'}
                     style={gridStyle}
                  >
                     <AgGridReact
                        theme="legacy"
                        rowData={tableData}
                        columnDefs={getColumnDefs()}
                        defaultColDef={{
                           sortable: true,
                           filter: true,
                           resizable: true,
                        }}
                        animateRows={true}
                     />
                  </div>
               )}
            </>
         )}

         {/* JSON View */}
         {viewMode === 'json' && (
            <div style={{
               background: 'var(--vscode-editor-background)',
               padding: '12px',
               borderRadius: '4px',
               border: '1px solid var(--vscode-panel-border)',
               maxHeight: '400px',
               overflow: 'auto',
               fontSize: '12px',
               fontFamily: 'var(--vscode-editor-font-family, monospace)',
            }}>
               <JSONTree
                  data={data}
                  theme={getJSONTreeTheme(isDark)}
                  invertTheme={false}
                  hideRoot={false}
                  shouldExpandNodeInitially={(keyPath, data, level) => level < 2}
               />
            </div>
         )}
      </div>
   );
};

interface ProfilerViewProps {
   metadata: any;
}

const ProfilerView: React.FC<ProfilerViewProps> = ({ metadata }) => {
   return (
      <div style={{
         padding: '20px',
         color: 'var(--vscode-foreground)',
         textAlign: 'center',
      }}>
         <div style={{ fontSize: '48px', marginBottom: '16px', opacity: 0.5 }}>
            📈
         </div>
         <div style={{ fontSize: '16px', fontWeight: '600', marginBottom: '8px' }}>
            Profiler View (Coming Soon)
         </div>
         <div style={{ fontSize: '13px', color: 'var(--vscode-descriptionForeground)' }}>
            Query execution profiling and performance metrics will be displayed here
         </div>
         {metadata && metadata.executionTime && (
            <div style={{
               marginTop: '20px',
               padding: '12px',
               background: 'var(--vscode-textBlockQuote-background)',
               borderLeft: '3px solid var(--vscode-textLink-foreground)',
               textAlign: 'left',
            }}>
               <strong>Execution Time:</strong> {metadata.executionTime}
            </div>
         )}
      </div>
   );
};

interface TaxiQLResultsProps {
   outputItem: OutputItem;
}

const TaxiQLResults: React.FC<TaxiQLResultsProps> = ({ outputItem }) => {
   const [activeTab, setActiveTab] = useState<'data' | 'profiler'>('data');
   const data = outputItem.json();

   return (
      <div style={{
         fontFamily: 'var(--vscode-font-family)',
         fontSize: 'var(--vscode-font-size)',
         color: 'var(--vscode-foreground)',
         padding: '12px',
         background: 'var(--vscode-editor-background)',
         border: '1px solid var(--vscode-panel-border)',
         borderRadius: '4px',
      }}>
         {/* Header with metadata */}
         {data.metadata && (
            <div style={{
               marginBottom: '12px',
               padding: '10px',
               background: 'var(--vscode-textBlockQuote-background)',
               borderRadius: '4px',
               fontSize: '12px',
               display: 'flex',
               gap: '16px',
               flexWrap: 'wrap',
            }}>
               {data.metadata.status && (
                  <span>
                     <strong>Status:</strong> {data.metadata.status}
                  </span>
               )}
               {data.metadata.rowCount !== undefined && (
                  <span>
                     <strong>Rows:</strong> {data.metadata.rowCount}
                  </span>
               )}
               {data.metadata.executionTime && (
                  <span>
                     <strong>Time:</strong> {data.metadata.executionTime}
                  </span>
               )}
            </div>
         )}

         {/* Error/Warning Messages */}
         {data.metadata?.warnings && data.metadata.warnings.length > 0 && (
            <div style={{
               marginBottom: '12px',
               padding: '12px',
               background: 'var(--vscode-inputValidation-errorBackground)',
               border: '1px solid var(--vscode-inputValidation-errorBorder)',
               borderRadius: '4px',
               fontSize: '12px',
            }}>
               <div style={{
                  fontWeight: '600',
                  marginBottom: '8px',
                  color: 'var(--vscode-errorForeground)',
               }}>
                  {data.metadata.status === 'error' ? 'Errors' : 'Warnings'}:
               </div>
               {data.metadata.warnings.map((warning: any, index: number) => (
                  <div key={index} style={{
                     marginBottom: index < data.metadata.warnings.length - 1 ? '8px' : '0',
                     paddingLeft: '8px',
                     borderLeft: '2px solid var(--vscode-errorForeground)',
                  }}>
                     {typeof warning === 'string' ? warning : warning.message || JSON.stringify(warning)}
                  </div>
               ))}
            </div>
         )}

         {/* Tab Navigation */}
         <div style={{
            display: 'flex',
            gap: '4px',
            marginBottom: '12px',
            borderBottom: '1px solid var(--vscode-panel-border)',
         }}>
            <button
               onClick={() => setActiveTab('data')}
               style={{
                  padding: '8px 16px',
                  border: 'none',
                  background: 'transparent',
                  color: 'var(--vscode-foreground)',
                  cursor: 'pointer',
                  fontSize: '13px',
                  fontWeight: '500',
                  borderBottom: activeTab === 'data' ? '2px solid var(--vscode-textLink-foreground)' : '2px solid transparent',
               }}
            >
               Data
            </button>
            <button
               onClick={() => setActiveTab('profiler')}
               style={{
                  padding: '8px 16px',
                  border: 'none',
                  background: 'transparent',
                  color: 'var(--vscode-foreground)',
                  cursor: 'pointer',
                  fontSize: '13px',
                  fontWeight: '500',
                  borderBottom: activeTab === 'profiler' ? '2px solid var(--vscode-textLink-foreground)' : '2px solid transparent',
               }}
            >
               Profiler
            </button>
         </div>

         {/* Tab Content */}
         {activeTab === 'data' && (
            <DataViewer data={data.data} contentType={data.contentType} />
         )}
         {activeTab === 'profiler' && (
            <ProfilerView metadata={data.metadata} />
         )}
      </div>
   );
};

// Query Plan Viewer Component
const QueryPlanViewer: React.FC<{ outputItem: OutputItem }> = ({ outputItem }) => {
   const data = outputItem.json();

   // The response structure is { diagramData: { diagramData: {...}, steps: [...], ... } }
   const queryPlanData = data.diagramData?.diagramData;

   console.log('QueryPlanViewer data:', data);
   console.log('Extracted queryPlanData:', queryPlanData);

   return (
      <div style={{
         padding: '16px',
         fontFamily: 'var(--vscode-font-family)',
         fontSize: 'var(--vscode-font-size)',
         color: 'var(--vscode-foreground)',
      }}>
         <h3 style={{
            marginTop: 0,
            marginBottom: '16px',
            fontSize: '16px',
            fontWeight: '600',
         }}>
            Query Plan
         </h3>

         {/* Query Plan Visualization */}
         {queryPlanData ? (
            <QueryPlanVisualization
               queryPlanData={queryPlanData as QueryPlanDiagramData}
               height={500}
            />
         ) : (
            <div style={{
               padding: '20px',
               background: 'var(--vscode-editor-background)',
               border: '1px solid var(--vscode-panel-border)',
               borderRadius: '4px',
            }}>
               No query plan data available
            </div>
         )}

         {/* Show any messages */}
         {data.diagramData?.queryExecutionMessages && data.diagramData.queryExecutionMessages.length > 0 && (
            <div style={{ marginTop: '16px' }}>
               <h4 style={{ fontSize: '14px', marginBottom: '8px' }}>Messages:</h4>
               {data.diagramData.queryExecutionMessages.map((msg: any, i: number) => (
                  <div key={i} style={{
                     padding: '8px',
                     marginBottom: '4px',
                     background: 'var(--vscode-inputValidation-infoBackground)',
                     border: '1px solid var(--vscode-inputValidation-infoBorder)',
                     borderRadius: '4px',
                     fontSize: '12px',
                  }}>
                     {msg.message || String(msg)}
                  </div>
               ))}
            </div>
         )}
      </div>
   );
};

// Store roots to reuse them instead of creating new ones
const rootMap = new WeakMap<HTMLElement, any>();

// Renderer activation function
export const activate = (context: RendererContext) => {
   console.log('TaxiQL Renderer activated');
   return {
      renderOutputItem(outputItem: OutputItem, element: HTMLElement) {
         const mimeType = outputItem.mime;
         console.log('Rendering output item with MIME type:', mimeType);
         console.log('Output data:', outputItem.json());

         // Reuse existing root or create a new one
         let root = rootMap.get(element);
         if (!root) {
            root = createRoot(element);
            rootMap.set(element, root);
         }

         // Render different components based on MIME type
         if (mimeType === 'application/vnd.taxi.queryplan+json') {
            console.log('Rendering QueryPlanViewer');
            root.render(<QueryPlanViewer outputItem={outputItem} />);
         } else {
            console.log('Rendering TaxiQLResults');
            root.render(<TaxiQLResults outputItem={outputItem} />);
         }
      },
   };
};
