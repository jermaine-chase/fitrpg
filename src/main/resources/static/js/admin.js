"use strict";

const LS = {
  token: 'ironpath_token', charId: 'ironpath_char_id', isAdmin: 'ironpath_is_admin',
  apiBase: 'ironpath_api_base',
};
const $ = sel => document.querySelector(sel);
const esc = s => String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));

/* Same-origin by default; override via `?api=` (persisted) or the topbar field —
 * shares the ironpath_api_base localStorage key with index.html. */
function resolveApiBase() {
  const fromQuery = new URLSearchParams(window.location.search).get('api');
  if (fromQuery !== null) {
    const trimmed = fromQuery.trim().replace(/\/+$/, '');
    if (trimmed) localStorage.setItem(LS.apiBase, trimmed);
    else localStorage.removeItem(LS.apiBase);
    return trimmed;
  }
  return (localStorage.getItem(LS.apiBase) || '').replace(/\/+$/, '');
}
let API_BASE = resolveApiBase();
function setApiBase(value) {
  const trimmed = (value || '').trim().replace(/\/+$/, '');
  if (trimmed) localStorage.setItem(LS.apiBase, trimmed);
  else localStorage.removeItem(LS.apiBase);
  API_BASE = trimmed;
}
const apiBase = () => API_BASE;

/* ========================== auth ==========================================
 * There's no separate admin login — this page reuses the same player JWT
 * that index.html stores in localStorage. Whether that account is actually
 * an admin is enforced server-side (403 on every /api/admin/** call
 * otherwise); this page just reacts to that by showing an access-denied
 * screen instead of the panel. */
function showAccessDenied(msg) {
  if (msg) $('#accessDeniedMsg').textContent = msg;
  $('#accessDenied').hidden = false;
  $('#adminPanel').hidden = true;
}
function showAdminPanel() { $('#accessDenied').hidden = true; $('#adminPanel').hidden = false; }

async function logout() {
  const token = localStorage.getItem(LS.token);
  if (token) {
    try {
      await fetch(apiBase() + '/api/auth/logout', {
        method: 'POST', headers: { 'Authorization': 'Bearer ' + token },
      });
    } catch (_) { /* best-effort */ }
  }
  localStorage.removeItem(LS.token);
  localStorage.removeItem(LS.charId);
  localStorage.removeItem(LS.isAdmin);
  window.location.href = 'index.html';
}

/* ========================== API layer ===================================== */
async function adminFetch(path, options = {}) {
  const token = localStorage.getItem(LS.token);
  const res = await fetch(apiBase() + path, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { 'Authorization': 'Bearer ' + token } : {}),
      ...(options.headers || {}),
    },
    ...options,
  });
  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try { const b = await res.json(); if (b && b.message) msg = b.message; } catch (_) {}
    const err = new Error(msg); err.status = res.status; throw err;
  }
  return res.status === 204 ? null : res.json();
}

/* ========================== flash message ================================= */
let flashTimer;
function flash(msg, type = 'ok') {
  const el = $('#flash');
  el.textContent = msg;
  el.className   = `flash show ${type}`;
  clearTimeout(flashTimer);
  flashTimer = setTimeout(() => el.classList.remove('show'), 4000);
}

/* ========================== quest table =================================== */
let questModalMode = 'create';
let questModalId   = null;

async function loadQuests() {
  const tbody = $('#questTbody');
  try {
    const quests = await adminFetch('/api/admin/quests');
    if (quests.length === 0) {
      tbody.innerHTML = '<tr class="empty-row"><td colspan="10">No quests found.</td></tr>';
      return;
    }
    tbody.innerHTML = quests.map(q => {
      const lc = (q.targetStat || '').toLowerCase();
      const statusClass = q.status === 'APPROVED' ? 'badge-dex' : q.status === 'REJECTED' ? 'badge-str' : 'badge-tier';
      return `<tr data-qid="${esc(q.questId)}" data-tag="${esc(q.tag || '')}" data-minutes="${q.estimatedMinutes || ''}" data-minlevel="${q.minLevel}">
        <td class="td-id">${esc(q.questId)}</td>
        <td class="td-title">${esc(q.title)}</td>
        <td class="td-desc">${esc(q.description || '—')}</td>
        <td><span class="badge badge-${lc}">${esc(q.targetStat)}</span></td>
        <td><span class="badge badge-tier">Tier ${tierLabel(q.minLevel)}</span> <span style="color:var(--dim);font-size:11px">≥${q.minLevel}</span></td>
        <td>${q.tag ? `<span class="badge badge-tag">${esc(q.tag)}</span>` : '<span style="color:var(--faint)">—</span>'}${q.estimatedMinutes ? ` <span style="color:var(--dim);font-size:11px">${q.estimatedMinutes}m</span>` : ''}</td>
        <td><span class="badge ${statusClass}">${esc(q.status || 'APPROVED')}</span></td>
        <td class="td-num">${q.baseCharacterXp}</td>
        <td class="td-num">${q.baseStatXp}</td>
        <td class="td-acts">
          <button class="btn btn-amber btn-sm q-edit" data-qid="${esc(q.questId)}">Edit</button>
          <button class="btn btn-danger btn-sm q-del"  data-qid="${esc(q.questId)}" data-title="${esc(q.title)}">Delete</button>
        </td>
      </tr>`;
    }).join('');
    tbody.querySelectorAll('.q-edit').forEach(btn => btn.addEventListener('click', () => openQuestModal('edit', btn.dataset.qid)));
    tbody.querySelectorAll('.q-del').forEach(btn  => btn.addEventListener('click', () => deleteQuest(btn.dataset.qid, btn.dataset.title)));
  } catch (e) {
    tbody.innerHTML = `<tr class="empty-row"><td colspan="10">Error loading quests: ${esc(e.message)}</td></tr>`;
  }
}

/* ========================== timed events ===================================== */
let eventModalId = null;

function fmtLocal(iso) {
  if (!iso) return '—';
  return iso.replace('T', ' ').slice(0, 16);
}
function toDatetimeLocalInput(iso) {
  return iso ? iso.slice(0, 16) : '';
}

async function loadEvents() {
  const tbody = $('#eventTbody');
  try {
    const events = await adminFetch('/api/admin/events');
    if (events.length === 0) {
      tbody.innerHTML = '<tr class="empty-row"><td colspan="6">No events configured.</td></tr>';
      return;
    }
    const now = new Date();
    tbody.innerHTML = events.map(e => {
      const active = new Date(e.startAt) <= now && now <= new Date(e.endAt);
      return `<tr data-eid="${esc(e.id)}" data-name="${esc(e.name)}" data-start="${esc(e.startAt)}" data-end="${esc(e.endAt)}" data-mult="${e.xpMultiplier}" data-stat="${esc(e.appliesToStat || '')}">
        <td class="td-title">${esc(e.name)} ${active ? '<span class="badge badge-dex">LIVE</span>' : ''}</td>
        <td style="color:var(--dim)">${fmtLocal(e.startAt)}</td>
        <td style="color:var(--dim)">${fmtLocal(e.endAt)}</td>
        <td class="td-num">${e.xpMultiplier}x</td>
        <td>${e.appliesToStat ? `<span class="badge badge-${e.appliesToStat.toLowerCase()}">${esc(e.appliesToStat)}</span>` : '<span style="color:var(--faint)">All</span>'}</td>
        <td class="td-acts">
          <button class="btn btn-amber btn-sm e-edit" data-eid="${esc(e.id)}">Edit</button>
          <button class="btn btn-danger btn-sm e-del" data-eid="${esc(e.id)}" data-name="${esc(e.name)}">Delete</button>
        </td>
      </tr>`;
    }).join('');
    tbody.querySelectorAll('.e-edit').forEach(btn => btn.addEventListener('click', () => openEventModal(btn.dataset.eid)));
    tbody.querySelectorAll('.e-del').forEach(btn  => btn.addEventListener('click', () => deleteEvent(btn.dataset.eid, btn.dataset.name)));
  } catch (e) {
    tbody.innerHTML = `<tr class="empty-row"><td colspan="6">Error loading events: ${esc(e.message)}</td></tr>`;
  }
}

function openEventModal(eventId) {
  eventModalId = eventId || null;
  $('#eventModalErr').textContent = '';
  $('#eventModalTitle').innerHTML = `<span class="glyph">✨</span> ${eventId ? 'Edit Event' : 'Add Event'}`;
  if (!eventId) {
    $('#ef-name').value = '';
    $('#ef-start').value = '';
    $('#ef-end').value = '';
    $('#ef-mult').value = '2.0';
    $('#ef-stat').value = '';
  } else {
    const row = document.querySelector(`[data-eid="${CSS.escape(eventId)}"]`);
    if (!row) return;
    $('#ef-name').value  = row.dataset.name;
    $('#ef-start').value = toDatetimeLocalInput(row.dataset.start);
    $('#ef-end').value   = toDatetimeLocalInput(row.dataset.end);
    $('#ef-mult').value  = row.dataset.mult;
    $('#ef-stat').value  = row.dataset.stat;
  }
  $('#eventOverlay').classList.add('show');
  $('#ef-name').focus();
}
function closeEventModal() { $('#eventOverlay').classList.remove('show'); }

async function saveEvent() {
  $('#eventModalErr').textContent = '';
  const body = {
    name: $('#ef-name').value.trim(),
    startAt: $('#ef-start').value,
    endAt: $('#ef-end').value,
    xpMultiplier: parseFloat($('#ef-mult').value),
    appliesToStat: $('#ef-stat').value || null,
  };
  if (!body.name) { $('#eventModalErr').textContent = 'Name is required.'; return; }
  if (!body.startAt || !body.endAt) { $('#eventModalErr').textContent = 'Start and end are required.'; return; }
  if (!body.xpMultiplier || body.xpMultiplier <= 0) { $('#eventModalErr').textContent = 'Multiplier must be positive.'; return; }

  const btn = $('#eventModalSave'); btn.disabled = true;
  try {
    if (eventModalId) {
      await adminFetch('/api/admin/events/' + eventModalId, { method: 'PUT', body: JSON.stringify(body) });
      flash('Event updated.');
    } else {
      await adminFetch('/api/admin/events', { method: 'POST', body: JSON.stringify(body) });
      flash('Event created.');
    }
    closeEventModal();
    await loadEvents();
  } catch (e) {
    $('#eventModalErr').textContent = e.message;
  } finally {
    btn.disabled = false;
  }
}

async function deleteEvent(id, name) {
  if (!confirm(`Delete event "${name}"?`)) return;
  try {
    await adminFetch('/api/admin/events/' + id, { method: 'DELETE' });
    flash(`Event "${name}" deleted.`);
    await loadEvents();
  } catch (e) {
    flash('Delete failed: ' + e.message, 'err');
  }
}

/* ========================== pending quest submissions ======================= */
async function loadPendingQuests() {
  const tbody = $('#pendingTbody');
  try {
    const quests = await adminFetch('/api/admin/quests/pending');
    if (quests.length === 0) {
      tbody.innerHTML = '<tr class="empty-row"><td colspan="7">No pending submissions.</td></tr>';
      return;
    }
    tbody.innerHTML = quests.map(q => {
      const lc = (q.targetStat || '').toLowerCase();
      return `<tr data-qid="${esc(q.questId)}">
        <td class="td-title">${esc(q.title)}</td>
        <td class="td-desc">${esc(q.description || '—')}</td>
        <td><span class="badge badge-${lc}">${esc(q.targetStat)}</span></td>
        <td>≥${q.minLevel}</td>
        <td class="td-num">${q.baseCharacterXp}</td>
        <td class="td-num">${q.baseStatXp}</td>
        <td class="td-acts">
          <button class="btn btn-dex btn-sm p-approve" data-qid="${esc(q.questId)}">Approve</button>
          <button class="btn btn-danger btn-sm p-reject" data-qid="${esc(q.questId)}">Reject</button>
        </td>
      </tr>`;
    }).join('');
    tbody.querySelectorAll('.p-approve').forEach(btn => btn.addEventListener('click', () => reviewQuest(btn.dataset.qid, 'approve')));
    tbody.querySelectorAll('.p-reject').forEach(btn  => btn.addEventListener('click', () => reviewQuest(btn.dataset.qid, 'reject')));
  } catch (e) {
    tbody.innerHTML = `<tr class="empty-row"><td colspan="7">Error loading submissions: ${esc(e.message)}</td></tr>`;
  }
}

async function reviewQuest(questId, action) {
  try {
    await adminFetch(`/api/admin/quests/${encodeURIComponent(questId)}/${action}`, { method: 'POST' });
    flash(`Quest ${action === 'approve' ? 'approved' : 'rejected'}.`);
    await Promise.all([loadPendingQuests(), loadQuests()]);
  } catch (e) {
    flash(`Failed to ${action}: ${e.message}`, 'err');
  }
}

function tierLabel(minLevel) {
  if (minLevel >= 50) return 'IV';
  if (minLevel >= 25) return 'III';
  if (minLevel >= 10) return 'II';
  return 'I';
}

function openQuestModal(mode, questId) {
  questModalMode = mode;
  questModalId   = questId || null;
  $('#questModalErr').textContent = '';
  $('#questModalTitle').innerHTML = `<span class="glyph">▣</span> ${mode === 'create' ? 'Add Quest' : 'Edit Quest'}`;

  if (mode === 'create') {
    $('#qf-id').value       = '';
    $('#qf-id').readOnly    = false;
    $('#qf-title').value    = '';
    $('#qf-desc').value     = '';
    $('#qf-stat').value     = 'STR';
    $('#qf-minlevel').value = '1';
    $('#qf-charxp').value   = '50';
    $('#qf-statxp').value   = '50';
    $('#qf-tag').value      = '';
    $('#qf-minutes').value  = '';
  } else {
    const row = document.querySelector(`[data-qid="${CSS.escape(questId)}"]`);
    if (!row) return;
    const cells = row.querySelectorAll('td');
    $('#qf-id').value       = questId;
    $('#qf-id').readOnly    = true;
    $('#qf-title').value    = cells[1].textContent;
    $('#qf-desc').value     = cells[2].textContent === '—' ? '' : cells[2].textContent;
    $('#qf-stat').value     = row.querySelector('.badge').textContent;
    $('#qf-minlevel').value = row.dataset.minlevel || '1';
    $('#qf-charxp').value   = cells[7].textContent;
    $('#qf-statxp').value   = cells[8].textContent;
    $('#qf-tag').value      = row.dataset.tag || '';
    $('#qf-minutes').value  = row.dataset.minutes || '';
  }
  $('#questOverlay').classList.add('show');
  $('#qf-title').focus();
}

async function saveQuest() {
  $('#questModalErr').textContent = '';
  const body = {
    questId:          $('#qf-id').value.trim(),
    title:            $('#qf-title').value.trim(),
    description:      $('#qf-desc').value.trim(),
    targetStat:       $('#qf-stat').value,
    minLevel:         parseInt($('#qf-minlevel').value, 10) || 1,
    baseCharacterXp:  parseInt($('#qf-charxp').value, 10)  || 0,
    baseStatXp:       parseInt($('#qf-statxp').value, 10)  || 0,
    tag:              $('#qf-tag').value || null,
    estimatedMinutes: $('#qf-minutes').value ? parseInt($('#qf-minutes').value, 10) : null,
  };
  if (!body.title)   { $('#questModalErr').textContent = 'Title is required.'; return; }
  if (questModalMode === 'create' && !body.questId) { $('#questModalErr').textContent = 'Quest ID is required.'; return; }

  const btn = $('#questModalSave'); btn.disabled = true;
  try {
    if (questModalMode === 'create') {
      await adminFetch('/api/admin/quests', { method: 'POST', body: JSON.stringify(body) });
      flash('Quest created successfully.');
    } else {
      await adminFetch('/api/admin/quests/' + encodeURIComponent(questModalId), { method: 'PUT', body: JSON.stringify(body) });
      flash('Quest updated successfully.');
    }
    closeQuestModal();
    await loadQuests();
  } catch (e) {
    $('#questModalErr').textContent = e.message;
  } finally {
    btn.disabled = false;
  }
}

async function deleteQuest(questId, title) {
  if (!confirm(`Delete quest "${title}"?\n\nThis cannot be undone.`)) return;
  try {
    await adminFetch('/api/admin/quests/' + encodeURIComponent(questId), { method: 'DELETE' });
    flash(`Quest "${title}" deleted.`);
    await loadQuests();
  } catch (e) {
    flash('Delete failed: ' + e.message, 'err');
  }
}

function closeQuestModal() { $('#questOverlay').classList.remove('show'); }

/* ========================== character table ================================ */
async function loadCharacters() {
  const tbody = $('#charTbody');
  try {
    const chars = await adminFetch('/api/admin/characters');
    if (chars.length === 0) {
      tbody.innerHTML = '<tr class="empty-row"><td colspan="7">No characters found.</td></tr>';
      return;
    }
    tbody.innerHTML = chars.map(c => {
      const lastWO = c.lastWorkoutDate || '—';
      const created = c.createdAt ? c.createdAt.split('T')[0] : '—';
      return `<tr data-cid="${esc(c.id)}">
        <td class="td-title">${esc(c.characterName)}</td>
        <td><span class="badge badge-tier">LV ${c.currentLevel}</span></td>
        <td class="td-num">${c.overallXp} / ${c.xpForNextLevel}</td>
        <td>${c.streakCount} 🔥</td>
        <td style="color:var(--dim)">${esc(lastWO)}</td>
        <td style="color:var(--faint);font-size:12px">${esc(created)}</td>
        <td class="td-acts">
          <button class="btn btn-amber btn-sm c-edit" data-cid="${esc(c.id)}">Edit</button>
          <button class="btn btn-danger btn-sm c-del"  data-cid="${esc(c.id)}" data-name="${esc(c.characterName)}">Delete</button>
        </td>
      </tr>`;
    }).join('');
    tbody.querySelectorAll('.c-edit').forEach(btn => btn.addEventListener('click', () => openCharModal(btn.dataset.cid)));
    tbody.querySelectorAll('.c-del').forEach(btn  => btn.addEventListener('click', () => deleteChar(btn.dataset.cid, btn.dataset.name)));
  } catch (e) {
    tbody.innerHTML = `<tr class="empty-row"><td colspan="7">Error loading characters: ${esc(e.message)}</td></tr>`;
  }
}

function openCharModal(charId) {
  $('#charModalErr').textContent = '';
  const row   = document.querySelector(`[data-cid="${CSS.escape(charId)}"]`);
  if (!row) return;
  const cells = row.querySelectorAll('td');
  $('#cf-id').value     = charId;
  $('#cf-name').value   = cells[0].textContent;
  const lvlText         = row.querySelector('.badge-tier');
  $('#cf-level').value  = lvlText ? lvlText.textContent.replace('LV ','') : '1';
  const xpText          = cells[2].textContent.split('/')[0].trim();
  $('#cf-xp').value     = xpText;
  $('#cf-streak').value = cells[3].textContent.replace('🔥','').trim();
  $('#charOverlay').classList.add('show');
  $('#cf-name').focus();
}

async function saveChar() {
  $('#charModalErr').textContent = '';
  const charId = $('#cf-id').value;
  const body = {
    characterName: $('#cf-name').value.trim(),
    currentLevel:  parseInt($('#cf-level').value, 10)  || 1,
    overallXp:     parseInt($('#cf-xp').value, 10)     || 0,
    streakCount:   parseInt($('#cf-streak').value, 10) || 0,
  };
  if (!body.characterName) { $('#charModalErr').textContent = 'Name is required.'; return; }

  const btn = $('#charModalSave'); btn.disabled = true;
  try {
    await adminFetch('/api/admin/characters/' + charId, { method: 'PUT', body: JSON.stringify(body) });
    flash(`Character "${body.characterName}" updated.`);
    closeCharModal();
    await loadCharacters();
  } catch (e) {
    $('#charModalErr').textContent = e.message;
  } finally {
    btn.disabled = false;
  }
}

async function deleteChar(charId, name) {
  if (!confirm(`Permanently delete "${name}"?\n\nThis will remove the character, all stats, and all workout logs. This cannot be undone.`)) return;
  try {
    await adminFetch('/api/admin/characters/' + charId, { method: 'DELETE' });
    flash(`Character "${name}" deleted.`);
    await loadCharacters();
  } catch (e) {
    flash('Delete failed: ' + e.message, 'err');
  }
}

function closeCharModal() { $('#charOverlay').classList.remove('show'); }

/* ========================== boot ========================================== */
async function boot() {
  $('#logoutBtn').addEventListener('click', logout);
  $('#apiBaseInput').value = API_BASE;
  $('#apiBaseSaveBtn').addEventListener('click', () => {
    setApiBase($('#apiBaseInput').value);
    $('#apiBaseInput').value = API_BASE;
    flash(`API base set to ${API_BASE || 'same origin'} — reloading…`);
    setTimeout(() => window.location.reload(), 600);
  });

  /* quest modal wiring */
  $('#addQuestBtn').addEventListener('click',   () => openQuestModal('create'));
  $('#questModalSave').addEventListener('click',   saveQuest);
  $('#questModalCancel').addEventListener('click', closeQuestModal);
  $('#questOverlay').addEventListener('keydown', e => { if (e.key === 'Escape') closeQuestModal(); });

  /* character modal wiring */
  $('#charModalSave').addEventListener('click',   saveChar);
  $('#charModalCancel').addEventListener('click', closeCharModal);
  $('#charOverlay').addEventListener('keydown',   e => { if (e.key === 'Escape') closeCharModal(); });
  $('#refreshCharsBtn').addEventListener('click', loadCharacters);
  $('#refreshPendingBtn').addEventListener('click', loadPendingQuests);

  /* event modal wiring */
  $('#addEventBtn').addEventListener('click', () => openEventModal(null));
  $('#eventModalSave').addEventListener('click', saveEvent);
  $('#eventModalCancel').addEventListener('click', closeEventModal);
  $('#eventOverlay').addEventListener('keydown', e => { if (e.key === 'Escape') closeEventModal(); });

  const token = localStorage.getItem(LS.token);
  if (!token) {
    showAccessDenied("You're not signed in. Log in from the Operative Terminal first.");
    return;
  }

  try {
    // Probe first: loadQuests()/loadCharacters() swallow their own errors to
    // render an in-table message, so they won't surface a 401/403 here.
    await adminFetch('/api/admin/quests');
    showAdminPanel();
    await Promise.all([loadQuests(), loadCharacters(), loadPendingQuests(), loadEvents()]);
  } catch (e) {
    if (e.status === 401 || e.status === 403) {
      // The filter treats an invalid/expired token the same as no token at
      // all, so a 403 here covers both "not an admin" and "session expired."
      showAccessDenied('This account is not an administrator, or your session has expired — sign in again from the Operative Terminal.');
    } else {
      showAccessDenied(`Cannot connect to the API: ${e.message}`);
    }
  }
}
boot();

