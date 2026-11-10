// Shared flat ESLint config. Consumers: `export { default } from '@kyro/config/eslint'`
// or spread it: `export default [...kyro, { ...overrides }]`.
import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import globals from 'globals';

/** Base TypeScript rules (no React). */
export const base = [
  { ignores: ['**/node_modules/**', '**/dist/**', '**/build/**', '**/.next/**', '**/coverage/**', '**/*.d.ts'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    languageOptions: { globals: { ...globals.node, ...globals.browser } },
    rules: {
      '@typescript-eslint/consistent-type-imports': 'error',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
    },
  },
];

/** TypeScript + react-hooks + jsx-a11y presets for Next/React apps. */
export const react = [
  ...base,
  {
    files: ['**/*.{jsx,tsx}'],
    plugins: { 'react-hooks': reactHooks, 'jsx-a11y': jsxA11y },
    rules: {
      ...reactHooks.configs.recommended.rules,
      ...jsxA11y.flatConfigs.recommended.rules,
    },
    languageOptions: jsxA11y.flatConfigs.recommended.languageOptions,
  },
];

export default react;
