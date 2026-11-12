import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
    },
  },
  {
    // 页面与入口采用“挂载/切换 key 时加载数据”的常规模式，
    // 该规则会把这些同步 setState 全部判为错误，先降级以便保留可见性。
    files: ['src/pages/**/*.{ts,tsx}', 'src/hooks/**/*.ts', 'src/App.tsx'],
    rules: {
      'react-hooks/set-state-in-effect': 'warn',
    },
  },
  {
    // shadcn/ui 组件按官方惯例同时导出组件与 variants / hooks。
    files: ['src/components/ui/**/*.{ts,tsx}'],
    rules: {
      'react-refresh/only-export-components': 'off',
    },
  },
])
