import { highlightCode } from '../../../remark/utils'
import {
  CodeSnippet,
  CodeSnippetMap,
  HighlightedCodeSnippet,
  HighlightedCodeSnippetMap
} from '@/components/Guides/CodeSnippet';


export function highlightCodeSnippet(code:CodeSnippet): HighlightedCodeSnippet {
  return code.lang && code.lang === 'terminal' ? code.code : highlightCode(code.code, code.lang)
}
export function highlightCodeSnippets(codeSnippets: CodeSnippetMap): HighlightedCodeSnippetMap {
  const highlighted = {};
  Object.keys(codeSnippets).map(key => {
    const snippet = codeSnippets[key];
    highlighted[key] = highlightCodeSnippet(snippet);
  })
  return highlighted;
}
