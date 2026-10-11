const assert = require('node:assert/strict')
const path = require('node:path')
const vm = require('node:vm')

async function main() {
  const { build, loadConfigFromFile } = await import('vite')
  const root = path.resolve(__dirname, '..')
  const { config } = await loadConfigFromFile({ command: 'build', mode: 'production' }, path.join(root, 'vite.config.ts'))
  const entry = 'virtual:i18n-csp-test'
  const result = await build({
    configFile: false,
    root,
    logLevel: 'error',
    resolve: config.resolve,
    define: config.define,
    plugins: [{
      name: 'i18n-csp-test',
      resolveId: id => id === entry ? entry : undefined,
      load: id => id === entry ? `
        import { createI18n } from 'vue-i18n'
        const i18n = createI18n({
          legacy: false,
          locale: 'en',
          messages: {
            en: { greeting: 'Hello {name}', linked: '@:greeting', count: 'no items | one item | {count} items' },
            zh: { greeting: '你好 {name}' },
          },
        })
        export const greeting = i18n.global.t('greeting', { name: 'niko' })
        export const linked = i18n.global.t('linked', { name: 'niko' })
        export const count = i18n.global.t('count', 2)
        i18n.global.locale.value = 'zh'
        export const switched = i18n.global.t('greeting', { name: 'niko' })
      ` : undefined,
    }],
    build: {
      write: false,
      minify: false,
      rollupOptions: { input: entry, preserveEntrySignatures: 'strict', output: { format: 'iife', name: 'CspTranslations' } },
    },
  })
  const context = vm.createContext({}, { codeGeneration: { strings: false, wasm: false } })
  vm.runInContext(result.output.find(item => item.type === 'chunk').code, context)
  assert.equal(context.CspTranslations.greeting, 'Hello niko')
  assert.equal(context.CspTranslations.linked, 'Hello niko')
  assert.equal(context.CspTranslations.count, '2 items')
  assert.equal(context.CspTranslations.switched, '你好 niko')
  console.log('Production translations work with string code generation disabled')
}

main().catch(error => { console.error(error); process.exitCode = 1 })
