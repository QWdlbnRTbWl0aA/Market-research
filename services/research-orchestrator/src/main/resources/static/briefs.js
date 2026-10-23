(() => {
  'use strict';
  const byId = id => document.getElementById(id);
  const controls = {run: byId('run'), refresh: byId('refresh'), all: byId('download-all'),
    search: byId('search'), readiness: byId('readiness'), status: byId('status'),
    list: byId('brief-list'), count: byId('count'), detail: byId('detail'), meta: byId('run-meta')};
  const saved = new URLSearchParams(location.hash.slice(1));
  let runs = [], briefs = [], runId = saved.get('run'), selected = saved.get('brief');
  let runRequest = 0, detailRequest = 0;
  const api = '/v1/brief-library/runs';
  const runPath = () => `${api}/${encodeURIComponent(runId)}`;
  const node = (tag, text, className) => {
    const result = document.createElement(tag);
    if (text !== undefined && text !== null) result.textContent = String(text);
    if (['p', 'dd', 'li', 'h2', 'h3'].includes(tag)) result.setAttribute('dir', 'auto');
    if (className) result.className = className;
    return result;
  };
  const values = value => Array.isArray(value) ? value : [];
  const badge = readiness => node('span', readiness === 'READY' ? 'Ready specification' :
    readiness === 'REQUIRES_REVIEW' ? 'Requires review' : 'Readiness unspecified',
    `badge${readiness === 'READY' ? '' : ' review'}`);
  const paragraph = (parent, text, className) => parent.append(node('p', text || 'Not specified.', className));
  const list = (parent, items) => {
    const result = node('ul');
    for (const item of values(items)) result.append(node('li', item));
    if (result.children.length) parent.append(result);
    else paragraph(parent, 'None reported.', 'muted');
  };
  const fields = (parent, entries) => {
    const result = node('dl');
    for (const [title, value] of entries) {
      result.append(node('dt', title), node('dd', value ?? 'Not specified.'));
    }
    parent.append(result);
  };
  const section = (parent, title) => {
    const result = node('section', undefined, 'section'); result.append(node('h3', title)); parent.append(result); return result;
  };
  const download = (parent, title, url) => {
    const link = node('a', title, 'button'); link.href = url; link.setAttribute('download', ''); parent.append(link);
  };
  const bookmark = () => {
    const params = new URLSearchParams();
    if (runId) params.set('run', runId);
    if (selected) params.set('brief', selected);
    history.replaceState(null, '', `#${params}`);
  };
  async function json(url) {
    const response = await fetch(url, {headers: {Accept: 'application/json'}, cache: 'no-store'});
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return response.json();
  }
  function placeholder(title, message) {
    const panel = node('div', undefined, 'placeholder');
    panel.append(node('h2', title), node('p', message)); controls.detail.replaceChildren(panel);
  }
  function visibleBriefs() {
    const query = controls.search.value.trim().toLocaleLowerCase();
    return briefs.filter(brief => (!controls.readiness.value || brief.readiness === controls.readiness.value) &&
      `${brief.title} ${brief.audience}`.toLocaleLowerCase().includes(query));
  }
  function renderList() {
    const visible = visibleBriefs(); controls.list.replaceChildren();
    controls.count.textContent = `${visible.length} of ${briefs.length} completed briefs`;
    for (const brief of visible) {
      const card = node('button', undefined, 'brief-card'); card.type = 'button';
      card.setAttribute('aria-pressed', String(brief.jobId === selected));
      card.append(node('strong', brief.title), badge(brief.readiness),
        node('span', `${brief.fileCount} files · ${brief.unresolvedCount} unresolved`, 'muted'));
      card.addEventListener('click', () => openBrief(brief.jobId)); controls.list.append(card);
    }
    if (!visible.length) controls.list.append(node('p', briefs.length ? 'No briefs match these filters.' :
      'No completed briefs in this investigation yet.', 'muted'));
  }
  function renderDocument(document) {
    const brief = document.brief, recommendation = document.recommendation || {};
    const panel = controls.detail; panel.replaceChildren();
    const top = node('div', undefined, 'detail-top');
    top.append(badge(brief.readiness), node('h2', brief.product),
      node('p', `Iteration ${document.iteration} · Job ${document.jobId}`, 'meta'));
    const downloads = node('div', undefined, 'downloads');
    const url = `${runPath()}/briefs/${encodeURIComponent(document.jobId)}/download`;
    download(downloads, 'Download JSON', `${url}?format=json`);
    download(downloads, 'Download Markdown', `${url}?format=markdown`); top.append(downloads); panel.append(top);
    if (brief.readiness !== 'READY' || values(brief.unresolvedQuestions).length) {
      const warning = node('section', undefined, 'warning'); warning.append(node('h3', 'Review before production'));
      list(warning, brief.unresolvedQuestions); panel.append(warning);
    }
    fields(section(panel, 'Product & purpose'), [['For whom', brief.audience], ['Use case', brief.useCase],
      ['Business intent preserved', brief.intentPreserved === true ? 'Yes — as assessed by the model' : 'Not confirmed'],
      ['Intent rationale', brief.intentRationale]]);
    const files = section(panel, 'Files to produce');
    paragraph(files, 'These are instructions for future deliverables, not generated files.', 'muted');
    for (const file of values(brief.files)) {
      const item = node('div', undefined, 'spec-file'); item.append(node('h3', file.filename));
      paragraph(item, `${file.format} · ${file.pagesOrItems} pages/items · ${file.dimensions}`, 'meta');
      paragraph(item, file.contents); files.append(item);
    }
    const visual = brief.visual || {}, appearance = section(panel, 'Visual direction');
    fields(appearance, [['Style', visual.style], ['Typography', visual.typography], ['Layout', visual.layout]]);
    const palette = node('div', undefined, 'palette');
    for (const color of values(visual.palette)) {
      const chip = node('span', undefined, 'swatch'), swatch = node('i'); swatch.setAttribute('aria-hidden', 'true');
      if (/^#(?:[0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$/i.test(color)) swatch.style.backgroundColor = color;
      chip.append(swatch, node('span', color)); palette.append(chip);
    }
    appearance.append(palette); list(appearance, visual.constraints);
    paragraph(section(panel, 'Commercial context'), brief.commercialContext);
    list(section(panel, 'Assumptions'), brief.assumptions);
    list(section(panel, 'Acceptance checks'), brief.acceptanceChecks);
    const criteria = section(panel, 'Verifiable per-file criteria');
    for (const criterion of values(brief.criteria)) {
      const item = node('div', undefined, 'criterion');
      item.append(node('strong', `${criterion.file} · ${criterion.check}`));
      fields(item, [['Expected', criterion.expected], ['Verification method', criterion.method]]); criteria.append(item);
    }
    const references = section(panel, 'Source references'); paragraph(references, brief.referenceUse);
    for (const reference of values(document.references)) {
      const item = node('div', undefined, 'spec-file');
      if (/^\d+$/.test(reference.listingId)) {
        const link = node('a', reference.title || `Listing ${reference.listingId}`);
        link.href = `https://www.etsy.com/listing/${reference.listingId}`;
        link.target = '_blank'; link.rel = 'noopener noreferrer'; item.append(link);
      } else item.append(node('span', reference.title));
      paragraph(item, `Listing ${reference.listingId} · Observed price: ${reference.currency || ''} ${reference.price ?? 'unknown'}`, 'meta');
      references.append(item);
    }
    const business = node('details', undefined, 'section'); business.append(node('summary', 'Opportunity & business assumptions'));
    fields(business, [['Recommendation', recommendation.name], ['Confidence', recommendation.confidence],
      ['Confidence explanation', recommendation.confidenceExplanation],
      ['Proposed price (USD)', recommendation.proposedPriceUsd], ['Differentiation', recommendation.differentiation],
      ['Revenue hypothesis — unverified', recommendation.recurringRevenueHypothesis],
      ['Automation plan', recommendation.automationPlan], ['Manual effort assumptions', recommendation.manualWorkAssumptions],
      ['Seasonality', recommendation.seasonality], ['Experiment', recommendation.experiment],
      ['Opportunity relationship', recommendation.relationship], ['Relationship rationale', recommendation.changeReason],
      ['Opportunity ID', document.opportunityId]]);
    business.append(node('h3', 'Originally proposed contents')); list(business, recommendation.deliverables);
    business.append(node('h3', 'Counterevidence')); list(business, recommendation.counterevidence); panel.append(business);
    const raw = node('details', undefined, 'section'); raw.append(node('summary', 'Complete JSON document'), node('pre', JSON.stringify(document, null, 2)));
    panel.append(raw);
  }
  async function openBrief(jobId) {
    const request = ++detailRequest, fromRun = runId; selected = jobId; bookmark(); renderList();
    controls.detail.setAttribute('aria-busy', 'true'); placeholder('Loading brief…', 'Reading the saved specification.');
    try {
      const document = await json(`${runPath()}/briefs/${encodeURIComponent(jobId)}`);
      if (request === detailRequest && fromRun === runId) renderDocument(document);
    } catch (error) {
      if (request !== detailRequest || fromRun !== runId) return;
      placeholder('Could not load this brief.', `${error.message}. Refresh the library and try again.`);
    } finally { if (request === detailRequest) controls.detail.setAttribute('aria-busy', 'false'); }
  }
  async function loadRun() {
    const request = ++runRequest; ++detailRequest;
    briefs = []; controls.all.hidden = true; controls.refresh.disabled = true;
    controls.detail.setAttribute('aria-busy', 'false');
    placeholder('Loading saved briefs…', 'No research or generation is started by this page.'); renderList();
    const run = runs.find(value => value.id === runId);
    controls.meta.textContent = run ? `Current iteration ${run.iteration} · ${run.status} · ${run.pendingBriefs} pending briefs · ${run.id}` : '';
    try {
      const result = await json(`${runPath()}/briefs`);
      if (request !== runRequest) return;
      briefs = result; controls.status.className = '';
      controls.status.textContent = `${briefs.length} saved briefs. Ready is a model assessment, not completed product QA.`;
      controls.all.href = `${runPath()}/download`; controls.all.hidden = !briefs.length;
      controls.all.setAttribute('download', ''); renderList();
      const visible = visibleBriefs();
      const next = visible.find(brief => brief.jobId === selected) || visible[0];
      if (next) await openBrief(next.jobId);
      else { selected = null; bookmark(); placeholder('No completed briefs to show.', 'Choose another investigation or change the filters.'); }
    } catch (error) {
      if (request !== runRequest) return;
      controls.status.className = 'error'; controls.status.textContent = `Could not load briefs (${error.message}). Try Refresh.`;
      placeholder('Saved briefs are unavailable.', 'No data was changed. Check the local service and try Refresh.');
    } finally { if (request === runRequest) controls.refresh.disabled = false; }
  }
  async function loadRuns() {
    controls.refresh.disabled = true; controls.run.disabled = true;
    try {
      runs = await json(api); controls.run.replaceChildren();
      for (const run of runs) {
        const option = node('option', `${run.query} · ${run.availableBriefs} briefs · ${run.id.slice(0, 8)}`);
        option.value = run.id; controls.run.append(option);
      }
      if (!runs.length) {
        controls.run.append(node('option', 'No investigations yet')); controls.status.textContent = 'No saved investigations.';
        controls.meta.textContent = ''; controls.all.hidden = true; briefs = []; selected = null; renderList();
        placeholder('No saved briefs yet.', 'Run research separately; completed product briefs will appear here.'); return;
      }
      if (!runs.some(run => run.id === runId)) runId = runs[0].id;
      controls.run.value = runId; controls.run.disabled = false; await loadRun();
    } catch (error) {
      controls.status.className = 'error'; controls.status.textContent = `Could not load investigations (${error.message}). Try Refresh.`;
    } finally { controls.refresh.disabled = false; }
  }
  controls.run.addEventListener('change', () => { runId = controls.run.value; selected = null; loadRun(); });
  controls.refresh.addEventListener('click', loadRuns);
  for (const control of [controls.search, controls.readiness]) control.addEventListener('input', () => {
    renderList(); const visible = visibleBriefs();
    if (!visible.some(brief => brief.jobId === selected)) {
      if (visible.length) openBrief(visible[0].jobId);
      else { ++detailRequest; selected = null; bookmark(); controls.detail.setAttribute('aria-busy', 'false');
        placeholder('No matching briefs.', 'Change the search text or readiness filter.'); }
    }
  });
  loadRuns();
})();
