/**
 * StubDesigner Component
 *
 * Detailed editor for creating/editing operation stubs.
 * Features:
 * - Simple mode: Single response editor with echo input option
 * - Advanced mode: Conditional responses based on input parameters
 * - Form mode and JSON mode for conditional responses
 * - Type viewer for reference
 */

import React, { useState, useEffect } from 'react';
import { OperationStub, ServiceMember, ResponseCondition, ParameterValue } from './types';

interface StubDesignerProps {
   stub: OperationStub;
   operation: ServiceMember;
   onSave: (stub: OperationStub) => void;
   onCancel: () => void;
   vscode: any; // VS Code API instance from parent
}

type ViewMode = 'simple' | 'advanced';
type AdvancedSubMode = 'form' | 'json';

export const StubDesigner: React.FC<StubDesignerProps> = ({ stub, operation, onSave, onCancel, vscode }) => {
   // Deep copy the stub to allow cancellation
   const [editedStub, setEditedStub] = useState<OperationStub>(() => JSON.parse(JSON.stringify(stub)));
   const [viewMode, setViewMode] = useState<ViewMode>('simple');
   const [advancedSubMode, setAdvancedSubMode] = useState<AdvancedSubMode>('form');
   const [expandedConditions, setExpandedConditions] = useState<Set<number>>(new Set());
   const [generatingPlaceholder, setGeneratingPlaceholder] = useState(false);

   // Check if echo input is available
   const canEchoInput = operation.parameters.length === 1 &&
      operation.parameters[0].type.qualifiedName === operation.returnType.qualifiedName;

   // Check if advanced mode is available
   const canUseAdvanced = operation.parameters.length > 0;

   // Listen for messages from the extension
   useEffect(() => {
      const handleMessage = (event: MessageEvent) => {
         const message = event.data;

         if (message.type === 'placeholderGenerated') {
            setEditedStub({
               ...editedStub,
               response: message.jsonStub,
            });
            setGeneratingPlaceholder(false);
         }
      };

      window.addEventListener('message', handleMessage);
      return () => window.removeEventListener('message', handleMessage);
   }, [editedStub]);

   const handleSave = () => {
      onSave(editedStub);
   };

   const handleGeneratePlaceholder = () => {
      setGeneratingPlaceholder(true);
      vscode.postMessage({
         type: 'generatePlaceholder',
         operationQualifiedName: operation.qualifiedName,
      });
   };

   const handleToggleEchoInput = () => {
      setEditedStub({
         ...editedStub,
         echoInput: !editedStub.echoInput,
         response: editedStub.echoInput ? editedStub.response : '',
      });
   };

   const handleSimpleResponseChange = (value: string) => {
      setEditedStub({
         ...editedStub,
         response: value,
      });
   };

   const handleAddCondition = () => {
      const newCondition: ResponseCondition = {
         inputs: operation.parameters.map(param => ({
            name: param.name,
            value: null,
         })),
         response: {
            body: '',
         },
      };

      const updatedConditions = [...(editedStub.conditionalResponses || []), newCondition];
      setEditedStub({
         ...editedStub,
         conditionalResponses: updatedConditions,
      });
      setExpandedConditions(new Set([...expandedConditions, updatedConditions.length - 1]));
   };

   const handleRemoveCondition = (index: number) => {
      const updatedConditions = editedStub.conditionalResponses?.filter((_, i) => i !== index) || [];
      setEditedStub({
         ...editedStub,
         conditionalResponses: updatedConditions.length > 0 ? updatedConditions : undefined,
      });
      const newExpanded = new Set(expandedConditions);
      newExpanded.delete(index);
      setExpandedConditions(newExpanded);
   };

   const handleConditionInputChange = (conditionIndex: number, paramName: string, value: any) => {
      const updatedConditions = [...(editedStub.conditionalResponses || [])];
      const condition = updatedConditions[conditionIndex];
      const inputIndex = condition.inputs.findIndex(input => input.name === paramName);

      if (inputIndex >= 0) {
         condition.inputs[inputIndex].value = value;
         setEditedStub({
            ...editedStub,
            conditionalResponses: updatedConditions,
         });
      }
   };

   const handleConditionResponseChange = (conditionIndex: number, body: string) => {
      const updatedConditions = [...(editedStub.conditionalResponses || [])];
      updatedConditions[conditionIndex].response.body = body;
      setEditedStub({
         ...editedStub,
         conditionalResponses: updatedConditions,
      });
   };

   const handleConditionalJsonChange = (jsonString: string) => {
      try {
         const parsed = JSON.parse(jsonString);
         setEditedStub({
            ...editedStub,
            conditionalResponses: parsed,
         });
      } catch (e) {
         console.error('Invalid JSON:', e);
      }
   };

   const toggleConditionExpanded = (index: number) => {
      const newExpanded = new Set(expandedConditions);
      if (newExpanded.has(index)) {
         newExpanded.delete(index);
      } else {
         newExpanded.add(index);
      }
      setExpandedConditions(newExpanded);
   };

   const getConditionSummary = (condition: ResponseCondition): string => {
      return condition.inputs
         .map(input => `${input.name}: ${JSON.stringify(input.value)}`)
         .join(', ');
   };

   return (
      <div style={{
         position: 'fixed',
         top: 0,
         left: 0,
         right: 0,
         bottom: 0,
         background: 'rgba(0, 0, 0, 0.5)',
         display: 'flex',
         alignItems: 'center',
         justifyContent: 'center',
         zIndex: 1000,
      }}>
         <div style={{
            background: 'var(--vscode-editor-background)',
            border: '1px solid var(--vscode-panel-border)',
            borderRadius: '4px',
            width: '900px',
            maxWidth: '90vw',
            height: '700px',
            maxHeight: '90vh',
            display: 'flex',
            flexDirection: 'column',
            boxShadow: '0 4px 20px rgba(0, 0, 0, 0.3)',
         }}>
            {/* Header */}
            <div style={{
               padding: '16px 20px',
               borderBottom: '1px solid var(--vscode-panel-border)',
               display: 'flex',
               justifyContent: 'space-between',
               alignItems: 'center',
            }}>
               <h3 style={{ margin: 0, fontSize: '14px', fontWeight: '600' }}>
                  Configure Stub: {operation.serviceName}.{operation.name}
               </h3>
               <button
                  onClick={onCancel}
                  style={{
                     background: 'transparent',
                     border: 'none',
                     color: 'var(--vscode-foreground)',
                     cursor: 'pointer',
                     fontSize: '20px',
                     padding: '0',
                     width: '24px',
                     height: '24px',
                  }}
               >
                  ✕
               </button>
            </div>

            {/* Mode Switcher */}
            <div style={{
               padding: '12px 20px',
               borderBottom: '1px solid var(--vscode-panel-border)',
               display: 'flex',
               gap: '8px',
            }}>
               <button
                  onClick={() => setViewMode('simple')}
                  style={{
                     padding: '6px 12px',
                     background: viewMode === 'simple' ? 'var(--vscode-button-background)' : 'transparent',
                     color: viewMode === 'simple' ? 'var(--vscode-button-foreground)' : 'var(--vscode-foreground)',
                     border: '1px solid var(--vscode-button-border)',
                     borderRadius: '2px',
                     cursor: 'pointer',
                     fontSize: '13px',
                  }}
               >
                  Simple
               </button>
               <button
                  onClick={() => canUseAdvanced && setViewMode('advanced')}
                  disabled={!canUseAdvanced}
                  style={{
                     padding: '6px 12px',
                     background: viewMode === 'advanced' ? 'var(--vscode-button-background)' : 'transparent',
                     color: viewMode === 'advanced' ? 'var(--vscode-button-foreground)' : 'var(--vscode-foreground)',
                     border: '1px solid var(--vscode-button-border)',
                     borderRadius: '2px',
                     cursor: canUseAdvanced ? 'pointer' : 'not-allowed',
                     opacity: canUseAdvanced ? 1 : 0.5,
                     fontSize: '13px',
                  }}
                  title={!canUseAdvanced ? `Disabled as ${operation.name} has no parameters` : ''}
               >
                  Advanced
               </button>
            </div>

            {/* Content Area */}
            <div style={{
               flexGrow: 1,
               overflow: 'auto',
               padding: '20px',
            }}>
               {viewMode === 'simple' ? (
                  <div style={{ display: 'flex', flexDirection: 'column', height: '100%', gap: '16px' }}>
                     {/* Echo Input Toggle */}
                     <div>
                        <label style={{
                           display: 'flex',
                           alignItems: 'center',
                           gap: '8px',
                           cursor: canEchoInput ? 'pointer' : 'not-allowed',
                           opacity: canEchoInput ? 1 : 0.5,
                        }}>
                           <input
                              type="checkbox"
                              checked={editedStub.echoInput || false}
                              onChange={handleToggleEchoInput}
                              disabled={!canEchoInput}
                           />
                           <span>Echo input as response</span>
                        </label>
                        {!canEchoInput && (
                           <div style={{
                              fontSize: '12px',
                              color: 'var(--vscode-descriptionForeground)',
                              marginTop: '4px',
                              marginLeft: '24px',
                           }}>
                              Only available when operation has exactly 1 parameter matching the return type
                           </div>
                        )}
                     </div>

                     {/* Conditional Responses Warning */}
                     {editedStub.conditionalResponses && editedStub.conditionalResponses.length > 0 && (
                        <div style={{
                           padding: '12px',
                           background: 'var(--vscode-inputValidation-warningBackground)',
                           border: '1px solid var(--vscode-inputValidation-warningBorder)',
                           borderRadius: '4px',
                           fontSize: '12px',
                        }}>
                           You already have conditional stubs, they will be favored over anything you configure here
                        </div>
                     )}

                     {/* Response Editor */}
                     {editedStub.echoInput ? (
                        <div style={{
                           padding: '20px',
                           background: 'var(--vscode-editor-background)',
                           border: '1px solid var(--vscode-panel-border)',
                           borderRadius: '4px',
                           textAlign: 'center',
                           color: 'var(--vscode-descriptionForeground)',
                        }}>
                           Operation will echo the provided input
                        </div>
                     ) : (
                        <div style={{ flexGrow: 1, display: 'flex', flexDirection: 'column' }}>
                           <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                              <label style={{ fontSize: '13px', fontWeight: '600' }}>
                                 Response Body (JSON):
                              </label>
                              <button
                                 onClick={handleGeneratePlaceholder}
                                 disabled={generatingPlaceholder}
                                 style={{
                                    padding: '4px 10px',
                                    background: 'var(--vscode-button-secondaryBackground)',
                                    color: 'var(--vscode-button-secondaryForeground)',
                                    border: '1px solid var(--vscode-button-border)',
                                    borderRadius: '2px',
                                    cursor: generatingPlaceholder ? 'not-allowed' : 'pointer',
                                    fontSize: '12px',
                                    opacity: generatingPlaceholder ? 0.5 : 1,
                                 }}
                                 title="Generate a placeholder stub based on the return type"
                              >
                                 {generatingPlaceholder ? 'Generating...' : 'Generate Placeholder'}
                              </button>
                           </div>
                           <textarea
                              value={editedStub.response}
                              onChange={(e) => handleSimpleResponseChange(e.target.value)}
                              style={{
                                 flexGrow: 1,
                                 fontFamily: 'var(--vscode-editor-font-family)',
                                 fontSize: '13px',
                                 padding: '12px',
                                 background: 'var(--vscode-editor-background)',
                                 color: 'var(--vscode-editor-foreground)',
                                 border: '1px solid var(--vscode-panel-border)',
                                 borderRadius: '4px',
                                 resize: 'none',
                              }}
                              placeholder='{"message": "Hello, World!"}'
                           />
                        </div>
                     )}
                  </div>
               ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', height: '100%', gap: '16px' }}>
                     {/* Advanced Sub-mode Switcher */}
                     <div style={{ display: 'flex', gap: '8px' }}>
                        <button
                           onClick={() => setAdvancedSubMode('form')}
                           style={{
                              padding: '4px 10px',
                              background: advancedSubMode === 'form' ? 'var(--vscode-button-background)' : 'transparent',
                              color: advancedSubMode === 'form' ? 'var(--vscode-button-foreground)' : 'var(--vscode-foreground)',
                              border: '1px solid var(--vscode-button-border)',
                              borderRadius: '2px',
                              cursor: 'pointer',
                              fontSize: '12px',
                           }}
                        >
                           Form
                        </button>
                        <button
                           onClick={() => setAdvancedSubMode('json')}
                           disabled={editedStub.echoInput}
                           style={{
                              padding: '4px 10px',
                              background: advancedSubMode === 'json' ? 'var(--vscode-button-background)' : 'transparent',
                              color: advancedSubMode === 'json' ? 'var(--vscode-button-foreground)' : 'var(--vscode-foreground)',
                              border: '1px solid var(--vscode-button-border)',
                              borderRadius: '2px',
                              cursor: editedStub.echoInput ? 'not-allowed' : 'pointer',
                              opacity: editedStub.echoInput ? 0.5 : 1,
                              fontSize: '12px',
                           }}
                        >
                           JSON
                        </button>
                     </div>

                     {advancedSubMode === 'form' ? (
                        <div style={{ flexGrow: 1, overflow: 'auto' }}>
                           {/* Conditional Responses List */}
                           {editedStub.conditionalResponses?.map((condition, index) => (
                              <div
                                 key={index}
                                 style={{
                                    marginBottom: '12px',
                                    border: '1px solid var(--vscode-panel-border)',
                                    borderRadius: '4px',
                                 }}
                              >
                                 {/* Accordion Header */}
                                 <div
                                    onClick={() => toggleConditionExpanded(index)}
                                    style={{
                                       padding: '12px',
                                       cursor: 'pointer',
                                       display: 'flex',
                                       justifyContent: 'space-between',
                                       alignItems: 'center',
                                       background: expandedConditions.has(index)
                                          ? 'var(--vscode-list-hoverBackground)'
                                          : 'transparent',
                                    }}
                                 >
                                    <span style={{ fontSize: '13px', fontFamily: 'var(--vscode-editor-font-family)' }}>
                                       {getConditionSummary(condition)}
                                    </span>
                                    <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                                       <button
                                          onClick={(e) => {
                                             e.stopPropagation();
                                             handleRemoveCondition(index);
                                          }}
                                          style={{
                                             background: 'transparent',
                                             border: 'none',
                                             color: 'var(--vscode-errorForeground)',
                                             cursor: 'pointer',
                                             padding: '4px',
                                          }}
                                       >
                                          ✕
                                       </button>
                                       <span>{expandedConditions.has(index) ? '▼' : '▶'}</span>
                                    </div>
                                 </div>

                                 {/* Accordion Content */}
                                 {expandedConditions.has(index) && (
                                    <div style={{ padding: '16px', borderTop: '1px solid var(--vscode-panel-border)' }}>
                                       {/* Parameters Section */}
                                       <div style={{ marginBottom: '16px' }}>
                                          <h4 style={{ fontSize: '13px', marginTop: 0, marginBottom: '12px' }}>Parameters</h4>
                                          {operation.parameters.map((param) => {
                                             const inputValue = condition.inputs.find(inp => inp.name === param.name);
                                             return (
                                                <div key={param.name} style={{ marginBottom: '12px' }}>
                                                   <label style={{ display: 'block', fontSize: '12px', marginBottom: '4px' }}>
                                                      {param.name}
                                                      <span style={{
                                                         marginLeft: '8px',
                                                         padding: '2px 6px',
                                                         background: 'var(--vscode-badge-background)',
                                                         color: 'var(--vscode-badge-foreground)',
                                                         borderRadius: '2px',
                                                         fontSize: '11px',
                                                      }}>
                                                         {param.type.typeName}
                                                      </span>
                                                   </label>
                                                   <input
                                                      type="text"
                                                      value={inputValue?.value ?? ''}
                                                      onChange={(e) => handleConditionInputChange(index, param.name, e.target.value)}
                                                      style={{
                                                         width: '100%',
                                                         padding: '6px 8px',
                                                         background: 'var(--vscode-input-background)',
                                                         color: 'var(--vscode-input-foreground)',
                                                         border: '1px solid var(--vscode-input-border)',
                                                         borderRadius: '2px',
                                                         fontFamily: 'inherit',
                                                         fontSize: '13px',
                                                      }}
                                                   />
                                                </div>
                                             );
                                          })}
                                       </div>

                                       {/* Response Section */}
                                       <div>
                                          <h4 style={{ fontSize: '13px', marginTop: 0, marginBottom: '8px' }}>Response Body</h4>
                                          <textarea
                                             value={condition.response.body}
                                             onChange={(e) => handleConditionResponseChange(index, e.target.value)}
                                             style={{
                                                width: '100%',
                                                height: '120px',
                                                fontFamily: 'var(--vscode-editor-font-family)',
                                                fontSize: '12px',
                                                padding: '8px',
                                                background: 'var(--vscode-editor-background)',
                                                color: 'var(--vscode-editor-foreground)',
                                                border: '1px solid var(--vscode-panel-border)',
                                                borderRadius: '4px',
                                                resize: 'vertical',
                                             }}
                                             placeholder='{"result": "value"}'
                                          />
                                       </div>
                                    </div>
                                 )}
                              </div>
                           ))}

                           {/* Add Condition Button */}
                           <button
                              onClick={handleAddCondition}
                              style={{
                                 padding: '8px 16px',
                                 background: 'var(--vscode-button-background)',
                                 color: 'var(--vscode-button-foreground)',
                                 border: 'none',
                                 borderRadius: '2px',
                                 cursor: 'pointer',
                                 fontSize: '13px',
                              }}
                           >
                              + Add Condition
                           </button>
                        </div>
                     ) : (
                        <div style={{ flexGrow: 1, display: 'flex', flexDirection: 'column' }}>
                           <label style={{ marginBottom: '8px', fontSize: '13px', fontWeight: '600' }}>
                              Conditional Responses (JSON):
                           </label>
                           <textarea
                              value={JSON.stringify(editedStub.conditionalResponses || [], null, 2)}
                              onChange={(e) => handleConditionalJsonChange(e.target.value)}
                              style={{
                                 flexGrow: 1,
                                 fontFamily: 'var(--vscode-editor-font-family)',
                                 fontSize: '12px',
                                 padding: '12px',
                                 background: 'var(--vscode-editor-background)',
                                 color: 'var(--vscode-editor-foreground)',
                                 border: '1px solid var(--vscode-panel-border)',
                                 borderRadius: '4px',
                                 resize: 'none',
                              }}
                           />
                        </div>
                     )}
                  </div>
               )}
            </div>

            {/* Footer */}
            <div style={{
               padding: '16px 20px',
               borderTop: '1px solid var(--vscode-panel-border)',
               display: 'flex',
               justifyContent: 'flex-end',
               gap: '10px',
            }}>
               <button
                  onClick={onCancel}
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
                  Update
               </button>
            </div>
         </div>
      </div>
   );
};
