import { highlightJson } from './json-highlight';

describe('highlightJson', () => {
  it('marks property keys, string values, numbers and literals distinctly', () => {
    const html = highlightJson('{ "lane": 1, "ok": true, "name": "race" }');
    expect(html).toContain('<span class="tok-key">"lane"</span>');
    expect(html).toContain('<span class="tok-number">1</span>');
    expect(html).toContain('<span class="tok-literal">true</span>');
    expect(html).toContain('<span class="tok-string">"race"</span>');
    // A value string is not mistaken for a key.
    expect(html).not.toContain('<span class="tok-key">"race"</span>');
  });

  it('escapes HTML in the source', () => {
    const html = highlightJson('{ "x": "a<b>c" }');
    expect(html).toContain('&lt;b&gt;');
    expect(html).not.toContain('<b>');
  });
});
