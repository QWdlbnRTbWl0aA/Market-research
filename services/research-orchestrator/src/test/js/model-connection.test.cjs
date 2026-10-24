const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/model-connection.js'), 'utf8');
const settle = async () => { for (let i = 0; i < 8; i++) await new Promise(resolve => setImmediate(resolve)); };
function fixture({destination = 'https://auth.openai.com/api/accounts/authorize?state=fixture', failure = false} = {}) {
  const elements = new Map(), calls = [], timers = [];
  const element = id => {
    if (!elements.has(id)) elements.set(id, {value: '', options: [], events: {},
      replaceChildren() {this.options = [];}, append(option) {this.options.push(option);},
      addEventListener(name, action) {this.events[name] = action;}});
    return elements.get(id);
  };
  vm.runInNewContext(source, {URL, Date, document: {getElementById: element, createElement: () => ({})},
    setInterval: action => {timers.push(action); return timers.length;}, clearInterval: () => {},
    fetch: async (url, options) => {
      calls.push({url, options});
      const body = url.endsWith('/sign-in') ? {authorizationUrl: destination, expiresAt: Date.now() + 600000} :
        {connected: false, planUsageEnabled: false, loginStatus: 'DISCONNECTED', callbackPort: 1456, models: []};
      return {ok: !failure, status: failure ? 400 : 200, headers: {get: () => null}, text: async () => JSON.stringify(body)};
    }});
  return {element, calls, timers};
}
test('connection flow uses its own endpoints and never starts analysis or transfers credentials', async () => {
  const {element, calls, timers} = fixture(); await settle();
  await element('connection-sign-in').events.click(); await settle();
  assert.equal(element('connection-authorize').hidden, false);
  assert.match(element('connection-authorize').href, /^https:\/\/auth\.openai\.com\//);
  assert.equal(calls[1].options.headers['X-Research-Action'], 'model-connection');
  assert.equal(calls[1].options.body, '{}');assert.equal(timers.length, 1);
  assert.ok(calls.every(call => call.url.startsWith('/v1/model-connection')));
  assert.match(element('connection-forward').textContent, /<your-SSH-destination>/);
});
test('a sign-in response cannot send the browser to an arbitrary site', async () => {
  const {element, timers} = fixture({destination: 'https://unexpected.invalid/login'}); await settle();
  await element('connection-sign-in').events.click(); await settle();
  assert.match(element('connection-status').textContent, /Invalid sign-in destination/);
  assert.equal(element('connection-authorize').href, undefined);assert.equal(timers.length, 0);
});
test('connection failures stay visible without affecting the saved-brief viewer', async () => {
  const {element, calls} = fixture({failure: true}); await settle();
  assert.match(element('connection-status').textContent, /Connection action failed/);
  assert.equal(calls.length, 1);
});
