/** Escapes the three characters that matter inside HTML text/attribute-free
    content, so a highlighted code string is safe for [innerHTML]. */
export function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
