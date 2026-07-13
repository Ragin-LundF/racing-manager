// Tiny, dependency-free JSON highlighter for the fixed samples on the docs
// page. Input is our own trusted constant and is fully HTML-escaped before any
// span is added, so the result is safe for [innerHTML]. A string immediately
// followed by a colon is treated as a property key.

import { escapeHtml } from './html-escape';

const TOKEN = new RegExp(
  [
    '("(?:\\\\.|[^"\\\\\\n])*")(\\s*:)?', // 1: string, 2: colon => key
    '\\b(true|false|null)\\b', // 3: literal
    '(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)', // 4: number
  ].join('|'),
  'g',
);

/** Returns HTML for [innerHTML]: escaped JSON with token spans. */
export function highlightJson(json: string): string {
  const regex = new RegExp(TOKEN.source, 'g');
  let out = '';
  let last = 0;
  let match: RegExpExecArray | null;

  while ((match = regex.exec(json)) !== null) {
    out += escapeHtml(json.slice(last, match.index));
    const [full, str, colon, literal, num] = match;
    if (str && colon) {
      out += `<span class="tok-key">${escapeHtml(str)}</span>${escapeHtml(colon)}`;
    } else if (str) {
      out += `<span class="tok-string">${escapeHtml(str)}</span>`;
    } else if (literal) {
      out += `<span class="tok-literal">${escapeHtml(literal)}</span>`;
    } else if (num) {
      out += `<span class="tok-number">${escapeHtml(num)}</span>`;
    }
    last = match.index + full.length;
    if (match.index === regex.lastIndex) {
      regex.lastIndex++;
    }
  }
  out += escapeHtml(json.slice(last));
  return out;
}
