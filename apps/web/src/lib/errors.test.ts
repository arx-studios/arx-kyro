import { describe, expect, it } from 'vitest';
import { safeNextPath } from './errors';

describe('safeNextPath', () => {
  it('allows same-site paths', () => {
    expect(safeNextPath('/discover?x=1')).toBe('/discover?x=1');
  });
  it('rejects external and protocol-relative targets', () => {
    expect(safeNextPath('https://evil.test')).toBe('/');
    expect(safeNextPath('//evil.test')).toBe('/');
    expect(safeNextPath('/\\evil.test')).toBe('/');
    expect(safeNextPath(null, '/home')).toBe('/home');
  });
});
