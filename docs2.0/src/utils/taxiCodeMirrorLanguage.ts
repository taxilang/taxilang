import { LanguageSupport, StreamLanguage } from '@codemirror/language';

// Define the Taxi language for CodeMirror
export function createTaxiLanguage() {
  return StreamLanguage.define({
    name: "taxi",
    startState: function() {
      return {
        tokenize: null,
        context: null,
        indented: 0,
        startOfLine: true,
      };
    },
    token: function(stream, state) {
      if (stream.eatSpace()) return null;

      if (stream.match(/\/\/.*$/)) return "comment";
      if (stream.match(/\/\*/)) {
        state.tokenize = multiLineComment;
        return multiLineComment(stream, state);
      }

      // Keywords
      if (stream.match(/\b(type|inherits|model|service|operation|query|given|as|alias|find|stream|by|table|namespace|import|from|declare|extension)\b/)) {
        return "keyword";
      }

      // Built-in types - treat as standard types (will get pink color like class names)
      if (stream.match(/\b(Array|Stream|Any|String|Number|Date|LocalDate|Instant|Decimal|Double|Void|Int|Boolean)\b/)) {
        return "type";
      }

      // Type names (capitalized words) - class names
      if (stream.match(/\b[A-Z][\w$]*\b/)) {
        return "type";
      }

      // String literals
      if (stream.match(/['"`]/)) {
        state.tokenize = stringLiteral(stream.current());
        return state.tokenize(stream, state);
      }

      // Numbers
      if (stream.match(/\b\d+(\.\d+)?\b/)) {
        return "number";
      }

      // Operators
      if (stream.match(/[+\-*\/%=<>!&|:]/)) {
        return "operator";
      }

      // Brackets and punctuation
      if (stream.match(/[{}\[\](),;.]/)) {
        return "punctuation";
      }

      // Identifiers
      if (stream.match(/\b[\w$]+\b/)) {
        return "variable";
      }

      stream.next();
      return null;
    },
  });
}

// Helper for multi-line comments
function multiLineComment(stream, state) {
  var ch;
  while ((ch = stream.next()) != null) {
    if (ch === "*" && stream.eat("/")) {
      state.tokenize = null;
      break;
    }
  }
  return "comment";
}

// Helper for string literals
function stringLiteral(quote) {
  return function(stream, state) {
    var escaped = false, ch;
    while ((ch = stream.next()) != null) {
      if (ch === quote && !escaped) {
        state.tokenize = null;
        break;
      }
      escaped = !escaped && ch === "\\";
    }
    return "string";
  };
}

// Export the Taxi language support
export function taxi() {
  return new LanguageSupport(createTaxiLanguage());
}