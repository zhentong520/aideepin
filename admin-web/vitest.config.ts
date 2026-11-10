import { defineConfig } from 'vitest/config';
import path from 'path';

// OPT-13: minimal smoke-level vitest for admin-web (pure-logic suites only).
// jsdom is required because DOMPurify needs a DOM implementation (happy-dom
// returns empty sanitize output — incompatible with DOMPurify's DOM walks).
// harness-01 (2026-11-09): add '#wf-shared-vendor' alias (same mapping as
// vite.config.ts:57) so future suites that reach into packages/aideepin-wf-shared
// don't fail at collection like user-web's did.
export default defineConfig({
  resolve: {
    alias: {
      '#wf-shared-vendor': path.resolve(process.cwd(), 'src/wf-shared-vendor.ts'),
    },
  },
  test: {
    environment: 'jsdom',
    include: ['tests/**/*.spec.ts'],
  },
});
