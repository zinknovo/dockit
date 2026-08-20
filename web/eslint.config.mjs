import { defineConfig, globalIgnores } from 'eslint/config'
import nextVitals from 'eslint-config-next/core-web-vitals'
import nextTs from 'eslint-config-next/typescript'

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  globalIgnores(['.next/**', 'out/**', 'build/**', 'next-env.d.ts']),
  {
    rules: {
      // 项目惯用"挂载时拉取数据"模式（同步 setLoading 后再 await），与 React 19 新严格规则冲突
      'react-hooks/set-state-in-effect': 'off',
      // 工作台等页面在组件内部定义渲染辅助组件（闭包状态），刻意为之
      'react-hooks/static-components': 'off'
    }
  }
])

export default eslintConfig
