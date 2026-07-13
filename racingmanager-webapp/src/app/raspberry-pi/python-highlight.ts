// Tiny, dependency-free Python highlighter for the fixed example on the docs
// page. The input is our own trusted constant, and the output is fully
// HTML-escaped before any span is added, so it is safe for [innerHTML].
// Comments and strings are matched before keywords, so words inside them are
// never re-highlighted.

import { escapeHtml } from './html-escape';

const KEYWORDS =
  'def|class|async|await|import|from|as|for|in|while|if|elif|else|return|continue|break|with|and|or|not|is|None|True|False|try|except|finally|raise|lambda|pass|global|nonlocal|yield';
const BUILTINS = 'print|len|range|enumerate|str|int|float|dict|list|set|super|isinstance';

const TOKEN = new RegExp(
  [
    '(#[^\\n]*)', // 1: comment
    '("""[\\s\\S]*?"""|\'\'\'[\\s\\S]*?\'\'\'|"(?:\\\\.|[^"\\\\\\n])*"|\'(?:\\\\.|[^\'\\\\\\n])*\')', // 2: string
    `\\b(${KEYWORDS})\\b`, // 3: keyword
    `\\b(${BUILTINS})\\b`, // 4: builtin
    '\\b(\\d[\\d_]*\\.?\\d*)\\b', // 5: number
  ].join('|'),
  'g',
);

/** Returns HTML for [innerHTML]: escaped source with token spans. */
export function highlightPython(code: string): string {
  const regex = new RegExp(TOKEN.source, 'g');
  let out = '';
  let last = 0;
  let match: RegExpExecArray | null;

  while ((match = regex.exec(code)) !== null) {
    out += escapeHtml(code.slice(last, match.index));
    const [full, comment, str, keyword, builtin, num] = match;
    if (comment) {
      out += `<span class="tok-comment">${escapeHtml(comment)}</span>`;
    } else if (str) {
      out += `<span class="tok-string">${escapeHtml(str)}</span>`;
    } else if (keyword) {
      out += `<span class="tok-keyword">${escapeHtml(keyword)}</span>`;
    } else if (builtin) {
      out += `<span class="tok-builtin">${escapeHtml(builtin)}</span>`;
    } else if (num) {
      out += `<span class="tok-number">${escapeHtml(num)}</span>`;
    }
    last = match.index + full.length;
    if (match.index === regex.lastIndex) {
      regex.lastIndex++;
    }
  }
  out += escapeHtml(code.slice(last));
  return out;
}
