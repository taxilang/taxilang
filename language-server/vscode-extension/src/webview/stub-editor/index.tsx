/**
 * Stub Editor Webview Entry Point
 *
 * This is the main entry point for the stub editor webview.
 * It renders the StubPanel component which allows users to configure operation stubs.
 */

import React, { useState, useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { StubPanel } from './StubPanel';
import { OperationStub, ServiceMember, WebviewMessage, ExtensionMessage } from './types';

// Get VS Code API
declare const acquireVsCodeApi: any;
const vscode = acquireVsCodeApi();

const App: React.FC = () => {
   const [stubs, setStubs] = useState<OperationStub[]>([]);
   const [operations, setOperations] = useState<ServiceMember[]>([]);
   const [loading, setLoading] = useState(true);

   useEffect(() => {
      // Handle messages from the extension
      const handleMessage = (event: MessageEvent<ExtensionMessage>) => {
         const message = event.data;

         switch (message.type) {
            case 'init':
               setStubs(message.stubs);
               setOperations(message.operations);
               setLoading(false);
               break;
            case 'operationsLoaded':
               setOperations(message.operations);
               break;
         }
      };

      window.addEventListener('message', handleMessage);

      // Request initial data
      vscode.postMessage({ type: 'getOperations' } as WebviewMessage);

      return () => window.removeEventListener('message', handleMessage);
   }, []);

   const handleStubsChange = (updatedStubs: OperationStub[]) => {
      setStubs(updatedStubs);
   };

   const handleSave = () => {
      vscode.postMessage({ type: 'save', stubs } as WebviewMessage);
   };

   const handleCancel = () => {
      vscode.postMessage({ type: 'cancel' } as WebviewMessage);
   };

   if (loading) {
      return (
         <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            height: '100vh',
            fontFamily: 'var(--vscode-font-family)',
            color: 'var(--vscode-foreground)',
         }}>
            Loading...
         </div>
      );
   }

   return (
      <div style={{
         fontFamily: 'var(--vscode-font-family)',
         fontSize: 'var(--vscode-font-size)',
         color: 'var(--vscode-foreground)',
         padding: '20px',
         height: '100vh',
         display: 'flex',
         flexDirection: 'column',
      }}>
         <h2 style={{ marginTop: 0, marginBottom: '20px' }}>Configure Operation Stubs</h2>

         <div style={{ flexGrow: 1, overflow: 'auto', marginBottom: '20px' }}>
            <StubPanel
               stubs={stubs}
               operations={operations}
               onStubsChange={handleStubsChange}
               vscode={vscode}
            />
         </div>

         <div style={{
            display: 'flex',
            gap: '10px',
            justifyContent: 'flex-end',
            borderTop: '1px solid var(--vscode-panel-border)',
            paddingTop: '16px',
         }}>
            <button
               onClick={handleCancel}
               style={{
                  padding: '6px 14px',
                  background: 'transparent',
                  color: 'var(--vscode-button-secondaryForeground)',
                  border: '1px solid var(--vscode-button-border)',
                  borderRadius: '2px',
                  cursor: 'pointer',
                  fontFamily: 'inherit',
                  fontSize: 'inherit',
               }}
            >
               Cancel
            </button>
            <button
               onClick={handleSave}
               style={{
                  padding: '6px 14px',
                  background: 'var(--vscode-button-background)',
                  color: 'var(--vscode-button-foreground)',
                  border: 'none',
                  borderRadius: '2px',
                  cursor: 'pointer',
                  fontFamily: 'inherit',
                  fontSize: 'inherit',
               }}
            >
               Save
            </button>
         </div>
      </div>
   );
};

// Mount the app
const container = document.getElementById('root');
if (container) {
   const root = createRoot(container);
   root.render(<App />);
}
