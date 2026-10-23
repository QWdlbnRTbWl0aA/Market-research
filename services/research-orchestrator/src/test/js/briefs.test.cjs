const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/briefs.js'), 'utf8');

// A deliberately small DOM fixture for this buildless page; not a visual/browser-layout test.
class Element {
  constructor(tag) { this.tagName = tag; this.children = []; this.attributes = {}; this.events = {}; this.style = {}; this.value = ''; this.text = ''; }
  set textContent(value) { this.text = String(value); this.children = []; }
  get textContent() { return this.text + this.children.map(child => child.textContent).join(''); }
  get innerHTML() { throw new Error('Source content must never be interpreted as HTML'); }
  append(...children) { this.children.push(...children); }
  replaceChildren(...children) { this.text = ''; this.children = children; }
  setAttribute(name, value) { this.attributes[name] = value; }
  addEventListener(name, action) { this.events[name] = action; }
  async fire(name) { await this.events[name]?.(); await settle(); }
}
const settle = async () => { for (let i = 0; i < 8; i++) await new Promise(resolve => setImmediate(resolve)); };
function descendants(element) { return [element, ...element.children.flatMap(descendants)]; }
function fixture({slowFirst = false, failRuns = false, empty = false} = {}) {
  const controls = Object.fromEntries(['run', 'refresh', 'download-all', 'search', 'readiness', 'status', 'brief-list', 'count', 'detail', 'run-meta']
    .map(id => [id, new Element(id)]));
  const base = '/v1/brief-library/runs', calls = [];
  const summaries = [{jobId: 'ready', title: 'Learning pack', audience: 'Teachers', readiness: 'READY', fileCount: 1, unresolvedCount: 0},
    {jobId: 'review', title: '<img src=x onerror=alert(1)> Review pack', audience: 'Parents', readiness: 'REQUIRES_REVIEW', fileCount: 1, unresolvedCount: 1}];
  const document = id => ({jobId: id, runId: 'run-1', iteration: 1, opportunityId: `opportunity-${id}`,
    brief: {product: summaries.find(item => item.jobId === id).title, readiness: id === 'ready' ? 'READY' : 'REQUIRES_REVIEW',
      audience: 'Teachers', useCase: 'Learning', files: [{filename: 'pack.pdf', format: 'PDF', pagesOrItems: 1, dimensions: 'A4', contents: 'Specific page contents'}],
      unresolvedQuestions: id === 'review' ? ['Verify the Hebrew source text'] : [], assumptions: [], acceptanceChecks: [], criteria: [], listingIds: ['123']},
    recommendation: {confidence: 'LOW', proposedPriceUsd: 3}, references: [{listingId: '123', title: 'Example', price: 2, currency: 'USD'}]});
  let finishFirst;
  const pendingFirst = new Promise(resolve => { finishFirst = () => resolve(document('ready')); });
  const fetch = async (url, options) => {
    calls.push({url, options});
    if (url === base) {
      if (failRuns) return {ok: false, status: 503};
      return {ok: true, json: async () => empty ? [] : [{id: 'run-1', query: 'Jewish printables', iteration: 1, status: 'WATCHING', pendingBriefs: 0, availableBriefs: 2}]};
    }
    if (url === `${base}/run-1/briefs`) return {ok: true, json: async () => summaries};
    const id = url.split('/').at(-1);
    return {ok: true, json: async () => slowFirst && id === 'ready' ? pendingFirst : document(id)};
  };
  vm.runInNewContext(source, {document: {getElementById: id => controls[id], createElement: tag => new Element(tag)},
    fetch, URLSearchParams, location: {hash: ''}, history: {replaceState() {}}});
  return {controls, calls, finishFirst, recover: () => { failRuns = false; }};
}
test('renders complete saved data safely and exposes only read/download links', async () => {
  const {controls, calls} = fixture(); await settle();
  assert.equal(controls['brief-list'].children.length, 2);
  assert.match(controls.detail.textContent, /Specific page contents/);
  await controls['brief-list'].children[1].fire('click');
  assert.match(controls.detail.textContent, /Verify the Hebrew source text/);
  assert.match(controls.detail.textContent, /<img src=x/);
  assert.equal(descendants(controls.detail).some(item => item.tagName === 'img'), false);
  const links = descendants(controls.detail).filter(item => item.tagName === 'a');
  assert.ok(links.some(item => item.href.endsWith('/briefs/review/download?format=markdown')));
  assert.ok(links.some(item => item.href === 'https://www.etsy.com/listing/123'));
  assert.equal(controls['download-all'].href, '/v1/brief-library/runs/run-1/download');
  assert.ok(calls.every(item => item.url.startsWith('/v1/brief-library/') && !item.options.method));
});
test('search and readiness filters update counts and clear invisible selections', async () => {
  const {controls} = fixture(); await settle();
  controls.search.value = 'review'; await controls.search.fire('input');
  assert.equal(controls.count.textContent, '1 of 2 completed briefs');
  assert.match(controls.detail.textContent, /Verify the Hebrew source text/);
  controls.readiness.value = 'READY'; await controls.readiness.fire('input');
  assert.equal(controls.count.textContent, '0 of 2 completed briefs');
  assert.match(controls.detail.textContent, /No matching briefs/);
  controls.search.value = ''; await controls.search.fire('input');
  assert.match(controls.detail.textContent, /Learning pack/);
});
test('late responses cannot replace a newer selected brief', async () => {
  const {controls, finishFirst} = fixture({slowFirst: true}); await settle();
  await controls['brief-list'].children[1].fire('click');
  finishFirst(); await settle();
  assert.match(controls.detail.textContent, /Verify the Hebrew source text/);
  assert.equal(controls['brief-list'].children[1].attributes['aria-pressed'], 'true');
});
test('empty and unavailable libraries are visible and can be refreshed', async () => {
  const empty = fixture({empty: true}); await settle();
  assert.match(empty.controls.status.textContent, /No saved investigations/);
  assert.equal(empty.controls['download-all'].hidden, true);
  const failed = fixture({failRuns: true}); await settle();
  assert.match(failed.controls.status.textContent, /HTTP 503/);
  assert.equal(failed.controls.refresh.disabled, false);
  failed.recover(); await failed.controls.refresh.fire('click');
  assert.equal(failed.controls['brief-list'].children.length, 2);
});
