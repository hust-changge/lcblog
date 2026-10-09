const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const { createVNode } = require('vue')

const source = fs.readFileSync(path.join(__dirname, '../src/composables/comp-consumer.ts'), 'utf8')
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS },
})
const context = { exports: {}, require, useRoute: () => ({ name: 'Welcome' }) }
vm.runInNewContext(outputText, context)
const { getComp } = context.exports.useCompConsumer()

const page = { render() {} }
const first = getComp(createVNode(page, { generation: 1 }))
const reopened = getComp(createVNode(page, { generation: 2 }))
assert.notEqual(reopened, first, 'Reopening must use the current RouterView node')
assert.equal(reopened.props.generation, 2)
assert.equal(reopened.type.name, 'Welcome')
assert.equal(getComp(undefined), undefined)
// Route transitions require one element root, including page-level modals.
const { parse } = require('@vue/compiler-sfc')
const { parse: parseTemplate, NodeTypes } = require('@vue/compiler-dom')
for (const file of [
  'src/pages/blog/essay/publish/index.vue',
  'src/pages/blog/photo/index.vue',
  'src/components/term-manager/index.vue',
]) {
  const { descriptor } = parse(fs.readFileSync(path.join(__dirname, '..', file), 'utf8'))
  const roots = parseTemplate(descriptor.template.content).children.filter(node => node.type === NodeTypes.ELEMENT)
  assert.equal(roots.length, 1, `${file}: transitions cannot leave a fragment root`)
  assert.equal(roots[0].tag, 'div', `${file}: transitions need an actual element root`)
}
console.log('Page close/reopen node regression check passed')
