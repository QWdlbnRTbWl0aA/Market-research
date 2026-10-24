(() => {
  'use strict';
  const get = id => document.getElementById(id);
  const status = get('connection-status');
  let poll, expiresAt = 0;
  async function request(path = '', body) {
    const response = await fetch(`/v1/model-connection${path}`, {
      method: body === undefined ? 'GET' : 'POST', cache: 'no-store',
      headers: {Accept: 'application/json', ...(body === undefined ? {} : {'Content-Type': 'application/json', 'X-Research-Action': 'model-connection'})},
      ...(body === undefined ? {} : {body: JSON.stringify(body)})
    });
    if (!response.ok) throw new Error('Connection action failed. Check callback forwarding and ChatGPT plan permission, then retry.');
    return response.status === 204 || response.headers.get('content-length') === '0' ? null : response.text().then(text => text ? JSON.parse(text) : null);
  }
  function showModels(models, selected) {
    const select = get('connection-model'); select.replaceChildren();
    for (const model of models || []) {
      const option = document.createElement('option'); option.value = model.slug; option.textContent = model.displayName;
      select.append(option);
    }
    if (selected) select.value = selected;
    select.disabled = !select.options.length; get('connection-save-model').disabled = select.disabled;
  }
  async function refresh() {
    const connection = await request();
    status.textContent = connection.connected ? `${connection.account} · ${connection.planUsageEnabled ? 'ChatGPT plan permission enabled' : 'Plan permission required'} · ${connection.model || 'Load and select a model'}` : `ChatGPT: ${connection.loginStatus.toLowerCase().replaceAll('_', ' ')}. API fallback remains within its existing allowance.`;
    get('connection-forward').textContent = `ssh -N -L ${connection.callbackPort}:127.0.0.1:${connection.callbackPort} <your-SSH-destination>`;
    get('connection-models').disabled = !connection.connected || !connection.planUsageEnabled;
    showModels(connection.models, connection.model);
    if (poll && (['CONNECTED', 'SIGN_IN_FAILED', 'DECLINED', 'PLAN_PERMISSION_REQUIRED'].includes(connection.loginStatus) || Date.now() > expiresAt)) {
      clearInterval(poll); poll = undefined; get('connection-authorize').hidden = true;
      if (Date.now() > expiresAt && connection.loginStatus === 'AWAITING_BROWSER') status.textContent = 'Sign-in expired. Choose Continue with ChatGPT to try again.';
    }
  }
  async function action(operation) {
    try { await operation(); } catch (error) { status.textContent = error.message; }
  }
  get('connection-sign-in').addEventListener('click', () => action(async () => {
    const login = await request('/sign-in', {});
    const url = new URL(login.authorizationUrl);
    if (url.protocol !== 'https:' || url.hostname !== 'auth.openai.com') throw new Error('Invalid sign-in destination.');
    const link = get('connection-authorize'); link.href = url.href; link.hidden = false; expiresAt = login.expiresAt;
    status.textContent = 'Open the ChatGPT sign-in link, sign in and approve plan usage. Keep your port forward running.';
    if (poll) clearInterval(poll);
    poll = setInterval(() => action(refresh), 2500);
  }));
  get('connection-models').addEventListener('click', () => action(async () => {
    const models = await request('/models', {}); showModels(models); await refresh();
  }));
  get('connection-save-model').addEventListener('click', () => action(async () => {
    await request('/model', {model: get('connection-model').value}); await refresh();
  }));
  action(refresh);
})();
