import { highlightPython } from './python-highlight';

describe('highlightPython', () => {
  it('wraps keywords, strings, comments and numbers in token spans', () => {
    const html = highlightPython('def run():  # start\n    x = "hi"\n    return 42');
    expect(html).toContain('<span class="tok-keyword">def</span>');
    expect(html).toContain('<span class="tok-comment"># start</span>');
    expect(html).toContain('<span class="tok-string">"hi"</span>');
    expect(html).toContain('<span class="tok-keyword">return</span>');
    expect(html).toContain('<span class="tok-number">42</span>');
  });

  it('does not highlight keywords that appear inside strings or comments', () => {
    const html = highlightPython('x = "return None"  # def class');
    // The whole string stays one string token; no nested keyword spans inside it.
    expect(html).toContain('<span class="tok-string">"return None"</span>');
    expect(html).not.toContain('<span class="tok-string">"<span');
    expect(html).toContain('<span class="tok-comment"># def class</span>');
  });

  it('escapes HTML in the source', () => {
    const html = highlightPython('a = b < c and d > e');
    expect(html).toContain('&lt;');
    expect(html).toContain('&gt;');
    expect(html).not.toContain('<c');
  });
});
