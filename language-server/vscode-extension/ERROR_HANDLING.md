# Error Handling in TaxiQL Stub Editor

This document describes the comprehensive error handling implemented in the stub editor to ensure graceful degradation when JVM calls fail.

## Error Scenarios Handled

### 1. Language Client Not Available
**Scenario:** Language server hasn't started or isn't connected

**Handling:**
- Detect in `fetchOperations()` before making RPC call
- Send warning message to UI
- Fall back to stub data immediately
- Log warning to console

**User Experience:**
- Warning message: "Language server not connected. Using stub data."
- Stub operations displayed
- Status shows reason for fallback

### 2. RPC Request Timeout
**Scenario:** Language server is unresponsive or hung

**Handling:**
- 10-second timeout on RPC requests
- Use `Promise.race()` to enforce timeout
- Catch timeout error specifically
- Fall back to stub data

**User Experience:**
- Warning message: "Request timed out. Language server may be busy. Using stub data."
- No UI freeze
- Immediate feedback

### 3. Endpoint Not Implemented
**Scenario:** `taxiql/listOperations` not yet implemented in JVM

**Handling:**
- Catch "not found" or "No provider" errors
- Recognize as expected during development
- Fall back to stub data gracefully

**User Experience:**
- Warning message: "Language server endpoint not yet implemented. Using stub data."
- Clear indication this is expected
- Developer-friendly message

### 4. Connection Failure
**Scenario:** Network/IPC issues communicating with language server

**Handling:**
- Catch connection-related errors
- Specific error message about connectivity
- Fall back to stub data

**User Experience:**
- Warning message: "Failed to connect to language server. Using stub data."
- Clear indication of connection issue

### 5. Invalid Response Format
**Scenario:** Language server returns malformed data

**Handling:**
- Validate response has expected structure
- Check `response.operations` is an array
- Throw descriptive error if invalid
- Fall back to stub data

**User Experience:**
- Warning message: "Could not load operations from language server. Using stub data."
- No crash from malformed data

### 6. Webview Errors
**Scenario:** JavaScript errors in the webview UI

**Handling:**
- Global `window.addEventListener('error')` handler
- Catch and log all unhandled errors
- Display error message in UI
- Prevent complete UI breakdown

**User Experience:**
- Error displayed in operations container
- Status shows error state
- Console shows details for debugging

### 7. Malformed Messages
**Scenario:** Invalid messages between webview and extension

**Handling:**
- Validate message structure in `handleMessage()`
- Check message.type exists
- Handle unknown message types gracefully
- Log warnings for debugging

**User Experience:**
- No crash
- Warnings in console for developers
- Error notification sent to webview if applicable

### 8. UI Interaction Errors
**Scenario:** DOM elements missing or JavaScript errors in button handlers

**Handling:**
- Try-catch around `selectStub()` and `cancel()`
- Validate DOM elements exist
- Graceful error alerts
- Prevent crash on user interaction

**User Experience:**
- Alert: "An error occurred. Please try again."
- Logs detail for debugging
- Panel remains usable

## Error Flow Diagram

```
User Clicks "Attach Stubs"
    ↓
StubEditorPanel.show()
    ↓
Webview Loads
    ↓
Sends 'getOperations' message
    ↓
Extension receives message
    ↓
handleMessage() [try-catch wrapper]
    ↓
fetchOperations()
    ├─→ No language client?
    │   ├─→ Send operationsError
    │   └─→ Send stub operations (isStub: true)
    │
    ├─→ RPC call with timeout
    │   ├─→ Success
    │   │   ├─→ Validate response
    │   │   ├─→ Send operationsResponse (isStub: false)
    │   │   └─→ UI displays real data
    │   │
    │   └─→ Failure (timeout/not found/connection)
    │       ├─→ Determine error type
    │       ├─→ Send operationsError with user-friendly message
    │       └─→ Send stub operations (isStub: true)
    │
    └─→ Webview receives response
        ├─→ operationsLoading: Show loading state
        ├─→ operationsError: Display warning
        └─→ operationsResponse: Display operations with stub badge if applicable
```

## Implementation Details

### Extension Side (stubEditor.ts)

```typescript
private async fetchOperations(projectRoot: string) {
    // Send loading state
    this.panel.webview.postMessage({ type: "operationsLoading" });

    // Check client availability
    if (!this.languageClient) {
        this.sendOperationsError("Language server not connected. Using stub data.");
        this.sendStubOperations();
        return;
    }

    try {
        // Add timeout
        const timeoutPromise = new Promise<never>((_, reject) => {
            setTimeout(() => reject(new Error("Request timeout")), 10000);
        });

        const requestPromise = this.languageClient.sendRequest<ListOperationsResponse>(
            TAXIQL_LIST_OPERATIONS,
            request
        );

        const response = await Promise.race([requestPromise, timeoutPromise]);

        // Validate response
        if (!response || !Array.isArray(response.operations)) {
            throw new Error("Invalid response format from language server");
        }

        // Success!
        this.panel.webview.postMessage({
            type: "operationsResponse",
            operations: response.operations,
            isStub: false,
        });
    } catch (error) {
        // Determine error type and send appropriate message
        const errorMessage = determineUserFriendlyError(error);
        this.sendOperationsError(errorMessage);
        this.sendStubOperations();
    }
}
```

### Webview Side (HTML/JS)

```javascript
// Global error handler
window.addEventListener('error', (event) => {
    log('Webview error: ' + event.error);
    showErrorInUI();
});

// Message handlers
window.addEventListener('message', event => {
    switch (event.data.type) {
        case 'operationsLoading':
            showLoadingState();
            break;
        case 'operationsError':
            showWarning(event.data.error);
            break;
        case 'operationsResponse':
            showOperations(event.data.operations, event.data.isStub);
            break;
    }
});

// UI interaction with error handling
function selectStub() {
    try {
        const stubId = document.getElementById('stubId').value.trim();
        if (!stubId) {
            alert('Please enter a stub ID');
            return;
        }
        vscode.postMessage({ type: 'selectStub', stubsId: stubId });
    } catch (error) {
        log('Error: ' + error);
        alert('An error occurred. Please try again.');
    }
}
```

## Visual Feedback

### Loading State
```
Status: Connecting to JVM...
Operations: ⏳ Loading operations from language server...
```

### Error/Warning State
```
Status: Using stub data (JVM endpoint not implemented)
Operations:
  ⚠️ [Warning box with specific message]
  ℹ️ Showing example operations. Implement taxiql/listOperations endpoint to see real data.
  [List of stub operations]
```

### Success State
```
Status: Connected to JVM ✓
Operations:
  [List of real operations from language server]
```

## Testing Error Scenarios

### 1. Test Language Server Not Running
```bash
# Stop language server
# Open stub editor
# Expected: Warning + stub data immediately
```

### 2. Test Timeout
```kotlin
// In JVM, add sleep to listOperations handler
Thread.sleep(15000)
// Expected: Timeout warning after 10s + stub data
```

### 3. Test Invalid Response
```kotlin
// Return null or invalid structure
return null
// Expected: Validation error + stub data
```

### 4. Test Endpoint Not Implemented
```
// Don't implement @JsonRequest handler yet
// Expected: "Not implemented" warning + stub data
```

### 5. Test Webview Errors
```javascript
// Inject error in webview
throw new Error("Test error");
// Expected: Global error handler catches + displays error
```

## Logging

All errors are logged to multiple locations for debugging:

1. **Extension Host Console** (Debug Console in VSCode)
   ```
   [Stub Editor] taxiql/listOperations failed: Request timeout
   ```

2. **Webview Console** (Browser DevTools)
   ```
   Stub editor loaded, requesting operations...
   Received 3 operations (stub data)
   ```

3. **Output Panel** (View > Output > Taxi Language Server)
   ```
   taxiql/listOperations endpoint not available, using stub
   ```

## Recovery Mechanisms

### Automatic Recovery
- Always fall back to stub data
- Never leave UI in broken state
- Always provide actionable feedback

### Manual Recovery
- User can refresh by clicking "Attach Stubs" again
- Stub ID can still be entered manually if operations list fails
- Cancel button always works (with fallback to window.close)

### Progressive Enhancement
- Stub data is fully functional
- When JVM endpoint is implemented, automatically switches to real data
- No code changes needed in extension when server is updated

## Benefits

1. **Resilient**: Handles all failure modes gracefully
2. **Developer-Friendly**: Clear messages about what's not implemented
3. **User-Friendly**: Always provides usable fallback
4. **Debuggable**: Comprehensive logging at all levels
5. **Maintainable**: Centralized error handling logic
6. **Testable**: Each error scenario can be tested independently

## Future Improvements

- [ ] Retry mechanism for transient errors
- [ ] Cache operations to reduce RPC calls
- [ ] Configurable timeout duration
- [ ] More detailed error diagnostics in UI
- [ ] Health check endpoint to verify server status
- [ ] Telemetry for tracking error rates

## Summary

The stub editor now has comprehensive error handling that ensures:
- ✅ Never crashes on RPC failures
- ✅ Always provides stub data as fallback
- ✅ Clear user feedback for all error states
- ✅ Detailed logging for debugging
- ✅ Graceful degradation at every level
- ✅ UI remains functional even during errors
- ✅ Developer-friendly messages during development
- ✅ No user-facing failures when JVM endpoint not implemented
