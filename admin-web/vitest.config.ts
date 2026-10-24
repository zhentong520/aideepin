import { defineConfig } from 'vitest/config';

// OPT-13: minimal smoke-level vitest for admin-web (pure-logic suites only).
// jsdom is required because DOMPurify needs a DOM implementation (happy-dom
// returns empty sanitize output — incompatible with DOMPurify's DOM walks).
export default defineConfig({
  test: {
    environment: 'jsdom',
    include: ['tests/**/*.spec.ts'],
  },
});
