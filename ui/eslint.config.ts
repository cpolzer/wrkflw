import js from '@eslint/js'
import vueTsEslintConfig from '@vue/eslint-config-typescript'

export default [
  js.configs.recommended,
  ...vueTsEslintConfig(),
  {
    ignores: ['dist/', 'node_modules/', 'src/api/types.ts'],
  },
]
