import { describe, expect, it } from 'vitest';
import { safeHtml } from '../src/utils/sanitize';

// OPT-13 smoke: the four T0-02 behavioral guarantees for the shared sanitizer.
describe('safeHtml', () => {
  it('strips onerror handlers from img tags', () => {
    const out = safeHtml('<img src=x onerror=alert(1)>');
    expect(out).not.toContain('onerror');
    expect(out).toContain('src="x"');
  });

  it('removes javascript: protocol links', () => {
    const out = safeHtml('<a href="javascript:alert(1)">x</a>');
    expect(out).not.toContain('javascript:');
  });

  it('removes script tags entirely', () => {
    const out = safeHtml('<script>alert(1)<\/script>hello');
    expect(out).not.toContain('<script');
    expect(out).toContain('hello');
  });

  it('keeps benign tags like <br>', () => {
    const out = safeHtml('line1<br>line2');
    expect(out).toContain('<br');
  });
});
