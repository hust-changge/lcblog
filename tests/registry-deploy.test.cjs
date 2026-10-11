const assert = require('node:assert/strict')
const { execFileSync } = require('node:child_process')
const path = require('node:path')

const root = path.resolve(__dirname, '..')
function config(registry, tag) {
  const args = ['compose', '--env-file', '.env.example', '-f', 'compose.yml']
  if (registry) args.push('-f', 'compose.registry.yml')
  args.push('config', '--format', 'json')
  return JSON.parse(execFileSync('docker', args, {
    cwd: root,
    env: { ...process.env, IMAGE_TAG: tag },
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  }))
}

const base = config(false, '')
const deployed = config(true, 'test-sha')
assert.equal(deployed.name, base.name)
assert.deepEqual(deployed.services.mysql, base.services.mysql)
assert.deepEqual(deployed.volumes, base.volumes)
assert.deepEqual(deployed.networks, base.networks)
for (const service of ['app', 'nginx']) {
  assert.equal(deployed.services[service].image, `ghcr.io/hust-changge/lcblog-${service}:test-sha`)
  const { image, ...remaining } = deployed.services[service]
  assert.deepEqual(remaining, base.services[service])
}
assert.throws(() => config(true, ''), /请设置构建成功的完整提交 SHA/)
console.log('Registry deployment preserves services/data and requires an explicit image version')
