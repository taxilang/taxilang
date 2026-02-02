/**
 * StubPanel Component
 *
 * Summary list view showing all configured stubs.
 * Features:
 * - Table with operation selector, response summary, and delete button
 * - Add new stub button
 * - Opens StubDesigner dialog for editing
 */

import React, { useState } from 'react';
import { OperationStub, ServiceMember } from './types';
import { StubDesigner } from './StubDesigner';

interface StubPanelProps {
   stubs: OperationStub[];
   operations: ServiceMember[];
   onStubsChange: (stubs: OperationStub[]) => void;
   vscode: any; // VS Code API instance
}

export const StubPanel: React.FC<StubPanelProps> = ({ stubs, operations, onStubsChange, vscode }) => {
   const [editingStub, setEditingStub] = useState<{ stub: OperationStub; index: number } | null>(null);

   const handleAddStub = () => {
      const newStub: OperationStub = {
         operationName: '',
         response: '',
      };
      onStubsChange([...stubs, newStub]);
   };

   const handleRemoveStub = (index: number) => {
      const updatedStubs = stubs.filter((_, i) => i !== index);
      onStubsChange(updatedStubs);
   };

   const handleOperationChange = (index: number, operationName: string) => {
      const updatedStubs = [...stubs];
      updatedStubs[index] = { ...updatedStubs[index], operationName };
      onStubsChange(updatedStubs);
   };

   const handleEditStub = (index: number) => {
      const stub = stubs[index];
      // Only allow editing if operation is selected
      if (!stub.operationName) {
         return;
      }
      setEditingStub({ stub, index });
   };

   const handleStubUpdated = (updatedStub: OperationStub) => {
      if (editingStub) {
         const updatedStubs = [...stubs];
         updatedStubs[editingStub.index] = updatedStub;
         onStubsChange(updatedStubs);
      }
      setEditingStub(null);
   };

   const handleStubEditCancelled = () => {
      setEditingStub(null);
   };

   const getResponseSummary = (stub: OperationStub): string => {
      if (stub.echoInput) {
         return 'Echoes input';
      }
      if (stub.conditionalResponses && stub.conditionalResponses.length > 0) {
         const count = stub.conditionalResponses.length;
         return `(${count}) conditional response${count !== 1 ? 's' : ''}`;
      }
      if (stub.response) {
         // Truncate long responses
         return stub.response.length > 50 ? stub.response.substring(0, 50) + '...' : stub.response;
      }
      return '';
   };

   const getOperationDisplayName = (operationName: string): string => {
      const operation = operations.find(op => op.qualifiedName === operationName);
      return operation ? operation.displayName : operationName;
   };

   return (
      <>
         <div style={{ marginBottom: '16px' }}>
            <table style={{
               width: '100%',
               borderCollapse: 'collapse',
               fontSize: '13px',
            }}>
               <thead>
                  <tr style={{
                     borderBottom: '1px solid var(--vscode-panel-border)',
                  }}>
                     <th style={{
                        textAlign: 'left',
                        padding: '8px',
                        fontWeight: '600',
                        width: '40%',
                     }}>
                        Operation
                     </th>
                     <th style={{
                        textAlign: 'left',
                        padding: '8px',
                        fontWeight: '600',
                        width: '50%',
                     }}>
                        Response
                     </th>
                     <th style={{
                        textAlign: 'center',
                        padding: '8px',
                        fontWeight: '600',
                        width: '10%',
                     }}>
                        Actions
                     </th>
                  </tr>
               </thead>
               <tbody>
                  {stubs.map((stub, index) => (
                     <tr
                        key={index}
                        style={{
                           borderBottom: '1px solid var(--vscode-panel-border)',
                        }}
                        onMouseEnter={(e) => {
                           e.currentTarget.style.backgroundColor = 'var(--vscode-list-hoverBackground)';
                        }}
                        onMouseLeave={(e) => {
                           e.currentTarget.style.backgroundColor = 'transparent';
                        }}
                     >
                        <td style={{ padding: '8px' }}>
                           <select
                              value={stub.operationName}
                              onChange={(e) => handleOperationChange(index, e.target.value)}
                              style={{
                                 width: '100%',
                                 padding: '4px 8px',
                                 background: 'var(--vscode-dropdown-background)',
                                 color: 'var(--vscode-dropdown-foreground)',
                                 border: '1px solid var(--vscode-dropdown-border)',
                                 borderRadius: '2px',
                                 fontFamily: 'inherit',
                                 fontSize: 'inherit',
                              }}
                           >
                              <option value="">Select operation...</option>
                              {operations.map((op) => (
                                 <option key={op.qualifiedName} value={op.qualifiedName}>
                                    {getOperationDisplayName(op.qualifiedName)}
                                 </option>
                              ))}
                           </select>
                        </td>
                        <td
                           style={{
                              padding: '8px',
                              fontFamily: 'var(--vscode-editor-font-family)',
                              fontSize: '0.85em',
                              color: stub.operationName ? 'var(--vscode-foreground)' : 'var(--vscode-disabledForeground)',
                              cursor: stub.operationName ? 'pointer' : 'default',
                              maxWidth: '300px',
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                              whiteSpace: 'nowrap',
                           }}
                           onClick={() => handleEditStub(index)}
                           title={stub.operationName ? 'Click to edit response' : 'Select an operation first'}
                        >
                           {stub.operationName ? getResponseSummary(stub) : 'Select an operation first'}
                        </td>
                        <td style={{ padding: '8px', textAlign: 'center' }}>
                           <button
                              onClick={() => handleRemoveStub(index)}
                              style={{
                                 background: 'transparent',
                                 border: 'none',
                                 color: 'var(--vscode-errorForeground)',
                                 cursor: 'pointer',
                                 padding: '4px 8px',
                                 borderRadius: '2px',
                                 fontSize: 'inherit',
                              }}
                              onMouseEnter={(e) => {
                                 e.currentTarget.style.backgroundColor = 'var(--vscode-list-hoverBackground)';
                              }}
                              onMouseLeave={(e) => {
                                 e.currentTarget.style.backgroundColor = 'transparent';
                              }}
                              title="Remove stub"
                           >
                              ✕
                           </button>
                        </td>
                     </tr>
                  ))}
               </tbody>
            </table>

            {stubs.length === 0 && (
               <div style={{
                  padding: '40px',
                  textAlign: 'center',
                  color: 'var(--vscode-descriptionForeground)',
               }}>
                  No stubs configured. Click "Add Stub" to get started.
               </div>
            )}
         </div>

         <button
            onClick={handleAddStub}
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
            + Add Stub
         </button>

         {/* Stub Designer Dialog */}
         {editingStub && (
            <StubDesigner
               stub={editingStub.stub}
               operation={operations.find(op => op.qualifiedName === editingStub.stub.operationName)!}
               onSave={handleStubUpdated}
               onCancel={handleStubEditCancelled}
               vscode={vscode}
            />
         )}
      </>
   );
};
