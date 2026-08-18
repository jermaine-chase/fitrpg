"use strict";

/* ========================== configuration ================================= */
const LS = {
  token:   'ironpath_token',
  charId:  'ironpath_char_id',
  isAdmin: 'ironpath_is_admin',
  apiBase: 'ironpath_api_base',
  log:     id => `ironpath_log_${id}`,
  done:    id => `ironpath_done_${id}`,
};

/*
 * Same-origin by default (Spring serves this page itself), so the app works
 * out of the box wherever it's deployed. If the frontend is ever hosted
 * separately from the API, repoint it via a `?api=https://host` query param
 * (persisted to localStorage on first use) or the API field in the Account
 * overlay.
 */
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
const STAT_ORDER  = ['STR', 'DEX', 'CON', 'WIL'];
const STAT_NAME   = { STR: 'Strength', DEX: 'Dexterity', CON: 'Constitution', WIL: 'Willpower' };
const QUEST_TAGS  = ['QUICK', 'INTENSE', 'RECOVERY', 'STRENGTH', 'CARDIO'];
const AVATAR_EMOJI = {
  wolf: '🐺', phoenix: '🔥', serpent: '🐍', golem: '🗿', raven: '🐦', tiger: '🐯',
  owl: '🦉', stag: '🦌', fox: '🦊', bear: '🐻', hawk: '🦅', turtle: '🐢',
};

const RANK_LABEL = minLevel => {
  if (minLevel >= 50) return 'B';
  if (minLevel >= 25) return 'C';
  if (minLevel >= 10) return 'D';
  return 'E';
};

/* Mirrors GameFormulas.questXpScale for reward preview. */
const questXpScale    = level => Math.pow(Math.max(1, level), 0.75);
const multiplierFor   = streak => 1 + Math.min(streak * 0.05, 0.50);
const pct             = (xp, max) => Math.max(0, Math.min(100, max > 0 ? (xp / max) * 100 : 0));
const $               = sel => document.querySelector(sel);
const esc             = s => String(s).replace(/[&<>"']/g, c => ({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;' }[c]));
const apiBase         = () => API_BASE;
const todayStr        = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; };

/* ========================== state ========================================= */
let state = { char: null, history: [], daily: null, dailyBonusMultiplier: 1 };
let ui    = { activeQuestId: null, checked: false, busy: false, pendingLevelUp: false, tagFilter: null };
let quests = [];
let prevStatPct = {};

/* ========================== audio cues (Web Audio API, no assets) ========== */
let audioCtx = null;
function getAudioCtx() {
  const AC = window.AudioContext || window.webkitAudioContext;
  if (!AC) return null;
  if (!audioCtx) audioCtx = new AC();
  if (audioCtx.state === 'suspended') audioCtx.resume().catch(() => {});
  return audioCtx;
}
function playTone(freq, startOffset, duration, type, gainPeak) {
  const ctx = getAudioCtx(); if (!ctx) return;
  const osc = ctx.createOscillator();
  const gain = ctx.createGain();
  osc.type = type || 'sine';
  osc.frequency.value = freq;
  const t0 = ctx.currentTime + startOffset;
  gain.gain.setValueAtTime(0.0001, t0);
  gain.gain.linearRampToValueAtTime(gainPeak, t0 + 0.02);
  gain.gain.exponentialRampToValueAtTime(0.0001, t0 + duration);
  osc.connect(gain).connect(ctx.destination);
  osc.start(t0);
  osc.stop(t0 + duration + 0.05);
}
function playXpGainCue()   { playTone(660, 0, 0.09, 'sine', 0.05); }
function playLevelUpCue()  { [523.25, 659.25, 783.99, 1046.5].forEach((f, i) => playTone(f, i * 0.09, 0.28, 'triangle', 0.16)); }
function playBonusCue()    { playTone(987.77, 0, 0.12, 'sine', 0.11); playTone(1318.51, 0.08, 0.2, 'sine', 0.11); }

/* ========================== visual feedback cues =========================== */
function flashPanel(kind) {
  const el = $('#hudPanel'); if (!el) return;
  const cls = 'flash-' + kind;
  el.classList.remove(cls);
  void el.offsetWidth; // force reflow so the animation restarts if retriggered quickly
  el.classList.add(cls);
  setTimeout(() => el.classList.remove(cls), 1200);
}
function pulseLevelBadge() {
  const el = $('#lvBadge'); if (!el) return;
  el.classList.remove('pulse');
  void el.offsetWidth;
  el.classList.add('pulse');
  setTimeout(() => el.classList.remove('pulse'), 800);
}

/* ========================== API layer ===================================== */
async function apiFetch(path, options = {}) {
  const token = localStorage.getItem(LS.token);
  const res = await fetch(apiBase() + path, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
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

const API = {
  register: (username, password, securityQuestion, securityAnswer) => apiFetch('/api/auth/register', { method: 'POST', body: JSON.stringify({ username, password, securityQuestion, securityAnswer }) }),
  login:   (username, password) => apiFetch('/api/auth/login',    { method: 'POST', body: JSON.stringify({ username, password }) }),
  logout: () => apiFetch('/api/auth/logout', { method: 'POST' }),
  forgotPassword: username => apiFetch('/api/auth/forgot-password', { method: 'POST', body: JSON.stringify({ username }) }),
  resetPassword: (username, securityAnswer, newPassword) => apiFetch('/api/auth/reset-password', { method: 'POST', body: JSON.stringify({ username, securityAnswer, newPassword }) }),
  getSecurityQuestion: () => apiFetch('/api/auth/security-question'),
  me: () => apiFetch('/api/auth/me'),
  setSecurityQuestion: (currentPassword, securityQuestion, securityAnswer) => apiFetch('/api/auth/security-question', { method: 'PUT', body: JSON.stringify({ currentPassword, securityQuestion, securityAnswer }) }),
  create: name   => apiFetch('/api/character', { method: 'POST', body: JSON.stringify({ characterName: name }) }),
  get:    id     => apiFetch('/api/character/' + id),
  getMine: ()    => apiFetch('/api/character/mine'),
  claim:  (id, questId) => apiFetch(`/api/character/${id}/claim`, { method: 'POST', body: JSON.stringify({ questId }) }),
  history: id    => apiFetch(`/api/character/${id}/history`),
  achievements: id => apiFetch(`/api/character/${id}/achievements`),
  leaderboard: (scope, metric) => apiFetch(`/api/leaderboard?scope=${encodeURIComponent(scope)}&metric=${encodeURIComponent(metric)}`),
  setCustomization: (id, avatarId, titleAchievementCode) => apiFetch(`/api/character/${id}/customization`, { method: 'PUT', body: JSON.stringify({ avatarId, titleAchievementCode }) }),
  submitQuest: body => apiFetch('/api/quests/submit', { method: 'POST', body: JSON.stringify(body) }),
  activeEvents: () => apiFetch('/api/events/active'),
  daily:   id    => apiFetch(`/api/character/${id}/daily`),
  quests: (level, tag) => apiFetch('/api/quests?level=' + level + (tag ? '&tag=' + encodeURIComponent(tag) : '')),
  remove: id     => apiFetch('/api/character/' + id, { method: 'DELETE' }),

  friends:          () => apiFetch('/api/friends'),
  incomingRequests: () => apiFetch('/api/friends/requests'),
  outgoingRequests: () => apiFetch('/api/friends/requests/sent'),
  sendFriendRequest: username => apiFetch('/api/friends/requests', { method: 'POST', body: JSON.stringify({ username }) }),
  acceptFriendRequest: id => apiFetch(`/api/friends/requests/${id}/accept`, { method: 'POST' }),
  declineFriendRequest: id => apiFetch(`/api/friends/requests/${id}/decline`, { method: 'POST' }),
  removeFriend: id => apiFetch('/api/friends/' + id, { method: 'DELETE' }),
  friendDetail: id => apiFetch('/api/friends/' + id),
  friendFeed: () => apiFetch('/api/friends/feed'),
  getFriendSettings: () => apiFetch('/api/friends/settings'),
  setFriendSettings: visibility => apiFetch('/api/friends/settings', { method: 'PUT', body: JSON.stringify({ visibility }) }),
  setFriendVisibility: (id, visibility) => apiFetch(`/api/friends/${id}/visibility`, { method: 'PUT', body: JSON.stringify({ visibility }) }),
};

/* ========================== quest loading ================================= */
async function loadQuests(level) {
  try {
    quests = await API.quests(level, ui.tagFilter);
    if (ui.activeQuestId && !quests.find(q => q.questId === ui.activeQuestId)) {
      ui.activeQuestId = null;
    }
  } catch (_) {
    quests = [];
  }
}

async function setTagFilter(tag) {
  ui.tagFilter = ui.tagFilter === tag ? null : tag;
  await loadQuests(state.char.currentLevel);
  render();
}

function renderTagChips() {
  const el = $('#tagChips'); if (!el) return;
  el.innerHTML = QUEST_TAGS.map(tag =>
    `<span class="tag-chip ${ui.tagFilter === tag ? 'active' : ''}" data-tag="${tag}">${tag}</span>`
  ).join('');
  el.querySelectorAll('.tag-chip').forEach(chip => {
    chip.addEventListener('click', () => setTagFilter(chip.dataset.tag));
  });
}

/* ========================== admin link visibility =========================== */
function cacheAdminFlag(isAdmin) {
  localStorage.setItem(LS.isAdmin, isAdmin ? '1' : '0');
  applyAdminLinkVisibility();
}
function applyAdminLinkVisibility() {
  $('#adminLink').hidden = localStorage.getItem(LS.isAdmin) !== '1';
}
async function refreshAdminFlag() {
  try {
    const me = await API.me();
    cacheAdminFlag(me.isAdmin);
  } catch (_) {
    // Keep whatever was last cached — a transient failure here shouldn't hide the link.
  }
}

/* ========================== daily focus quest =============================== */
async function loadDailyQuest() {
  try {
    const d = await API.daily(state.char.id);
    state.daily = d.quest.questId;
    state.dailyBonusMultiplier = d.bonusMultiplier;
  } catch (_) {
    state.daily = null;
    state.dailyBonusMultiplier = 1;
  }
}

/* ========================== progress dashboard ============================= */
async function loadProgress() {
  try {
    state.history = await API.history(state.char.id);
  } catch (_) {
    state.history = [];
  }
}

function renderProgress() {
  const empty = state.history.length === 0;
  $('#progressEmpty').hidden = !empty;
  $('#xpChart').style.display = empty ? 'none' : '';
  $('#statDistBar').style.display = empty ? 'none' : '';
  $('#statDistLegend').style.display = empty ? 'none' : '';
  if (empty) return;

  renderXpChart();
  renderStatDistribution();
}

/* Daily total XP over the last 14 days (including days with no claims). */
function renderXpChart() {
  const days = [];
  const today = new Date();
  for (let i = 13; i >= 0; i--) {
    const d = new Date(today); d.setDate(d.getDate() - i);
    days.push({ key: `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`, label: `${d.getMonth()+1}/${d.getDate()}`, xp: 0 });
  }
  const byDay = Object.fromEntries(days.map(d => [d.key, d]));
  state.history.forEach(log => {
    const key = (log.loggedAt || '').slice(0, 10);
    if (byDay[key]) byDay[key].xp += log.finalXpAwarded;
  });

  const max = Math.max(1, ...days.map(d => d.xp));
  const el = $('#xpChart');
  el.innerHTML = days.map((d, i) => {
    const heightPct = Math.round((d.xp / max) * 100);
    const showLabel = i % 3 === 0 || i === days.length - 1;
    return `<div class="xp-bar" tabindex="0" aria-label="${d.label}: ${d.xp} XP">
      <div class="xp-tooltip"><b>${d.xp}</b> XP · ${esc(d.label)}</div>
      <div class="xp-bar__fill" style="height:${Math.max(heightPct, d.xp > 0 ? 3 : 0)}%"></div>
      ${showLabel ? `<div class="xp-bar__date">${esc(d.label)}</div>` : '<div class="xp-bar__date">&nbsp;</div>'}
    </div>`;
  }).join('');
}

/* All-time proportion of XP earned per attribute, stacked into a single bar. */
function renderStatDistribution() {
  const totals = { STR: 0, DEX: 0, CON: 0, WIL: 0 };
  let grand = 0;
  state.history.forEach(log => {
    if (log.statType && totals.hasOwnProperty(log.statType)) {
      totals[log.statType] += log.finalXpAwarded;
      grand += log.finalXpAwarded;
    }
  });

  const bar = $('#statDistBar');
  const legend = $('#statDistLegend');
  if (grand === 0) {
    bar.innerHTML = '';
    legend.innerHTML = '<span class="stat-dist-legend__pct">No stat-tagged history yet.</span>';
    return;
  }

  bar.innerHTML = STAT_ORDER.map(type => {
    const pct = (totals[type] / grand) * 100;
    return pct > 0 ? `<div class="stat-dist-seg fill-${type.toLowerCase()}" style="width:${pct}%" title="${STAT_NAME[type]}: ${Math.round(pct)}%"></div>` : '';
  }).join('');

  legend.innerHTML = STAT_ORDER.map(type => {
    const pct = Math.round((totals[type] / grand) * 100);
    return `<span class="stat-dist-legend__item"><span class="stat-dist-legend__dot c-${type.toLowerCase()}" style="background:currentColor"></span>${STAT_NAME[type]} <span class="stat-dist-legend__pct">${pct}%</span></span>`;
  }).join('');
}

/* ========================== combat log ==================================== */
function loadLog(id)          { try { return JSON.parse(localStorage.getItem(LS.log(id))) || []; } catch (_) { return []; } }
function saveLog(id, lines)   { localStorage.setItem(LS.log(id), JSON.stringify(lines.slice(-80))); }
function pushLog(cat, html) {
  if (!state.char) return;
  const lines = loadLog(state.char.id);
  lines.push({ category: cat, html });
  saveLog(state.char.id, lines);
  renderLog();
}
const GLYPH = { system: '▣', quest: '✦', level: '↑', decay: '☠', bonus: '★', error: '!' };

/* ========================== done-today tracking =========================== */
function loadDone(id) {
  try { const d = JSON.parse(localStorage.getItem(LS.done(id))); if (d && d.date === todayStr()) return new Set(d.ids); } catch (_) {}
  return new Set();
}
function markDone(charId, questId) {
  const set = loadDone(charId); set.add(questId);
  localStorage.setItem(LS.done(charId), JSON.stringify({ date: todayStr(), ids: [...set] }));
}

/* ========================== rendering ===================================== */
function render() { renderHud(); renderStats(); renderEventBanner(); renderTagChips(); renderQuestBoard(); renderLog(); renderProgress(); }

function renderHud() {
  const c = state.char; if (!c) return;
  $('#charAvatar').textContent = AVATAR_EMOJI[c.avatarId] || '🐺';
  const titleEl = $('#charTitle');
  const titleName = c.titleAchievementCode ? titleNameFor(c.titleAchievementCode) : null;
  titleEl.hidden = !titleName;
  if (titleName) titleEl.textContent = `“${titleName}”`;
  $('#charName').textContent  = c.characterName;
  $('#charLevel').textContent = c.currentLevel;
  $('#charXp').textContent    = c.overallXp;
  $('#charXpMax').textContent = c.xpForNextLevel;
  const meter = $('#charXpMeter');
  if (meter) {
    meter.setAttribute('aria-valuenow', c.overallXp);
    meter.setAttribute('aria-valuemax', c.xpForNextLevel);
    meter.setAttribute('aria-valuetext', `${c.overallXp} of ${c.xpForNextLevel} XP`);
  }

  const fill = $('#charXpFill');
  const targetPct = pct(c.overallXp, c.xpForNextLevel);
  if (ui.pendingLevelUp) {
    // Fill to 100% (old level), snap back to empty, then animate up to the
    // carried-over XP so a level-up reads as "topped off, then restarted".
    ui.pendingLevelUp = false;
    fill.style.transition = 'none';
    fill.style.width = '100%';
    requestAnimationFrame(() => {
      fill.style.transition = 'none';
      fill.style.width = '0%';
      requestAnimationFrame(() => {
        fill.style.transition = '';
        fill.style.width = targetPct + '%';
      });
    });
  } else {
    fill.style.width = targetPct + '%';
  }
  $('#streakDay').textContent = c.streakCount;
  $('#buffVal').textContent   = multiplierFor(c.streakCount).toFixed(2);
  $('#streakFreezeCount').textContent = c.streakFreezesAvailable;

  const riskEl = $('#streakRisk');
  const atRisk = c.streakCount > 0 && c.lastWorkoutDate !== todayStr();
  riskEl.hidden = !atRisk;
  if (atRisk) {
    riskEl.textContent = c.streakFreezesAvailable > 0
      ? `Streak open until midnight — one quest keeps DAY ${c.streakCount} going. A missed day would burn a 🧊 freeze to preserve it instead of halving.`
      : `Streak open until midnight — one quest keeps DAY ${c.streakCount} going. A missed day halves it, it won't reset to zero.`;
  }
}

function renderStats() {
  const c = state.char; if (!c) return;
  const byType = Object.fromEntries(c.stats.map(s => [s.statType, s]));
  $('#statList').innerHTML = STAT_ORDER.map(type => {
    const s = byType[type]; if (!s) return '';
    const rusty = (s.status || '').toLowerCase() === 'rusty';
    const lc = type.toLowerCase();
    const target = pct(s.currentXp, s.xpForNextLevel);
    // Rebuilt from scratch each render, so start the bar at its previous width
    // (if known) and animate to the target on the next frame — otherwise the
    // CSS width transition has no "before" state to animate from.
    const start = prevStatPct[type] != null ? prevStatPct[type] : target;
    return `
      <div class="stat">
        <div class="stat__head">
          <span class="stat__tag c-${lc}">${type}</span>
          <span class="stat__name">${esc(STAT_NAME[type] || type)}</span>
          <span class="stat__lv">LV ${s.currentLevel}</span>
          <span class="stat__status ${rusty ? 'is-rusty' : 'is-active'}">${rusty ? 'Rusty' : 'Active'}</span>
        </div>
        <div class="bar stat__bar" role="meter" aria-label="${esc(STAT_NAME[type] || type)} XP" aria-valuemin="0" aria-valuenow="${s.currentXp}" aria-valuemax="${s.xpForNextLevel}"><div class="bar__fill fill-${lc}" data-target="${target}" style="width:${start}%"></div></div>
        <div class="stat__xp">${s.currentXp} / ${s.xpForNextLevel} XP</div>
      </div>`;
  }).join('');

  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      $('#statList').querySelectorAll('.bar__fill[data-target]').forEach(el => {
        el.style.width = el.dataset.target + '%';
      });
    });
  });
  STAT_ORDER.forEach(type => {
    const s = byType[type];
    if (s) prevStatPct[type] = pct(s.currentXp, s.xpForNextLevel);
  });
}

function renderQuestBoard() {
  const c = state.char;
  if (!c || quests.length === 0) {
    $('#questList').innerHTML = ui.tagFilter
      ? `<p class="quest-empty">No ${esc(ui.tagFilter)} quests unlocked yet.</p>`
      : '<p class="quest-empty">No quests available — check API connection.</p>';
    return;
  }

  const done      = loadDone(c.id);
  const scale     = questXpScale(c.currentLevel);
  const mult      = multiplierFor(c.streakCount);

  $('#questList').innerHTML = quests.map(q => {
    const lc       = q.targetStat.toLowerCase();
    const rank     = RANK_LABEL(q.minLevel);
    const cleared  = done.has(q.questId);
    const isActive = q.questId === ui.activeQuestId;
    const isDaily  = q.questId === state.daily && !cleared;
    const dailyMult = isDaily ? state.dailyBonusMultiplier : 1;
    const eventMult = eventMultiplierFor(q.targetStat);
    const estStat  = Math.round(q.baseStatXp      * scale * mult * dailyMult * eventMult);
    const estChar  = Math.round(q.baseCharacterXp * scale * mult * dailyMult * eventMult);

    return `
      <div class="qcard ${isActive ? 'is-active' : ''} ${cleared ? 'is-cleared' : ''} ${isDaily ? 'is-daily' : ''}" data-qid="${esc(q.questId)}">
        <div class="qcard__header">
          <span class="qcard__rank c-${lc}">${rank}·${q.targetStat}</span>
          ${q.tag ? `<span class="tag-chip" style="cursor:default;">${esc(q.tag)}</span>` : ''}
          ${isDaily ? '<span class="daily-badge" title="Bonus XP for completing today\'s Daily Focus">★ Daily Focus</span>' : ''}
          <span class="qcard__title">${esc(q.title)}</span>
          <span class="qcard__meta">${q.estimatedMinutes ? `~${q.estimatedMinutes}min · ` : ''}~${estStat+estChar} XP</span>
          <span class="qcard__chevron">▾</span>
        </div>
        <div class="qcard__body">
          ${q.description ? `<p class="qcard__desc">${esc(q.description)}</p>` : ''}
          ${cleared ? '' : `
            <label class="obj">
              <input type="checkbox" class="qc-check" data-qid="${esc(q.questId)}" ${isActive && ui.checked ? 'checked' : ''}>
              <span class="obj__box"></span>
              <span class="obj__text">Complete the objective above</span>
            </label>`}
          <div class="rewards">
            <div class="rewards__label">Rewards · ${mult.toFixed(2)}x streak · ${scale.toFixed(1)}x level${eventMult !== 1 ? ` · ${eventMult.toFixed(2)}x event` : ''}${isDaily ? ` · ${dailyMult.toFixed(2)}x daily focus` : ''}</div>
            <div class="rewards__line">
              <span class="reward c-${lc}">~+${estStat} ${q.targetStat} XP</span>
              <span class="reward">~+${estChar} Character XP</span>
            </div>
          </div>
          <button class="complete" data-qid="${esc(q.questId)}"
            ${cleared ? 'disabled' : ((!isActive || !ui.checked || ui.busy) ? 'disabled' : '')}>
            ${cleared ? 'Quest Cleared ✓' : (ui.busy && isActive ? 'Submitting…' : 'Complete Objective')}
          </button>
        </div>
      </div>`;
  }).join('');

  /* wire events */
  $('#questList').querySelectorAll('.qcard__header').forEach(hdr => {
    hdr.addEventListener('click', () => {
      const qid = hdr.closest('.qcard').dataset.qid;
      if (ui.activeQuestId === qid) {
        ui.activeQuestId = null;
      } else {
        ui.activeQuestId = qid;
        ui.checked = false;
      }
      renderQuestBoard();
    });
  });
  $('#questList').querySelectorAll('.qc-check').forEach(chk => {
    chk.addEventListener('change', () => {
      ui.checked = chk.checked;
      renderQuestBoard();
    });
  });
  $('#questList').querySelectorAll('.complete').forEach(btn => {
    if (!btn.disabled) btn.addEventListener('click', () => claimQuest(btn.dataset.qid));
  });
}

function renderLog() {
  if (!state.char) return;
  const body = $('#logBody');
  body.innerHTML = loadLog(state.char.id).map(l =>
    `<div class="line cat-${l.category}"><span class="line__glyph">${GLYPH[l.category] || '·'}</span><span class="line__text">${l.html}</span></div>`
  ).join('');
  body.scrollTop = body.scrollHeight;
}

/* ========================== connection chip =============================== */
function setConn(ok) {
  const dot = $('#connDot'), txt = $('#connText');
  dot.className = 'dot ' + (ok ? 'ok' : 'bad');
  const label = apiBase() ? apiBase().replace(/^https?:\/\//, '') : 'same origin';
  txt.textContent = ok ? 'linked · ' + label : 'offline';
}
function showBanner(msg) { const b = $('#banner'); b.innerHTML = msg; b.classList.add('show'); }
function hideBanner()    { $('#banner').classList.remove('show'); }

/* ========================== actions ======================================= */
async function claimQuest(questId) {
  if (ui.busy || !state.char) return;
  ui.busy = true; renderQuestBoard();

  const prev = state.char;
  try {
    const result = await API.claim(state.char.id, questId);
    state.char = result.character;
    setConn(true); hideBanner();
    markDone(state.char.id, questId);
    logClaimOutcome(questId, prev, result);

    const leveledUp = result.character.currentLevel > prev.currentLevel;
    const bonusRolled = !!result.bonusChallenge;
    ui.pendingLevelUp = leveledUp;
    if (leveledUp) { playLevelUpCue(); flashPanel('levelup'); pulseLevelBadge(); }
    else playXpGainCue();
    if (bonusRolled) { playBonusCue(); flashPanel('bonus'); }
    if (result.newAchievements && result.newAchievements.length > 0) {
      showAchievementToasts(result.newAchievements);
      result.newAchievements.forEach(a => pushLog('system', `🏅 Badge unlocked — <span class="hl">${esc(a.name)}</span>`));
    }

    // re-fetch quests if the character leveled up (new tiers may unlock)
    if (leveledUp) {
      await loadQuests(result.character.currentLevel);
    }

    await loadProgress();
    await loadDailyQuest();
    ui.checked = false; ui.busy = false;
    render();
  } catch (e) {
    ui.busy = false; renderQuestBoard();
    handleError(e, 'claim quest');
  }
}

function logClaimOutcome(questId, prev, result) {
  const next   = result.character;
  const q      = quests.find(x => x.questId === questId) || { title: questId, targetStat: 'STR', baseStatXp: 0, baseCharacterXp: 0 };
  const lc     = q.targetStat.toLowerCase();
  const scale  = questXpScale(prev.currentLevel);
  const mult   = multiplierFor(prev.streakCount);
  const bonusMult = result.bonusChallenge ? 1.5 : 1;
  const estStat   = Math.round(q.baseStatXp      * scale * mult * bonusMult);
  const estChar   = Math.round(q.baseCharacterXp * scale * mult * bonusMult);

  pushLog('quest', `Quest cleared — <span class="hl">${esc(q.title)}</span>`);
  pushLog('quest', `<span class="c-${lc}">+${estStat} ${q.targetStat} XP</span> · +${estChar} Character XP <span style="color:var(--faint)">(x${(scale * mult * bonusMult).toFixed(2)})</span>`);

  if (result.bonusChallenge) {
    pushLog('bonus', `Bonus Challenge: <span class="hl">${esc(result.bonusChallenge.description)}</span> <span style="color:var(--wil)">+${result.bonusChallenge.bonusXp} bonus XP</span>`);
  }

  if (result.dailyFocusBonusXp > 0) {
    pushLog('bonus', `Daily Focus cleared — <span class="hl">+${result.dailyFocusBonusXp} bonus XP</span>`);
  }

  if (next.streakCount > prev.streakCount)
    pushLog('system', `Streak extended — <span class="hl">DAY ${next.streakCount}</span>`);
  else if (next.streakCount < prev.streakCount)
    pushLog('decay', `Streak fractured — soft landing to <span class="hl">DAY ${next.streakCount}</span>`);
  else if (next.streakFreezesAvailable < prev.streakFreezesAvailable)
    pushLog('system', `🧊 Streak freeze consumed — <span class="hl">DAY ${next.streakCount}</span> preserved (${next.streakFreezesAvailable} left)`);
  if (next.streakFreezesAvailable > prev.streakFreezesAvailable)
    pushLog('system', `🧊 Streak freeze earned — <span class="hl">${next.streakFreezesAvailable} available</span>`);

  if (next.currentLevel > prev.currentLevel)
    pushLog('level', `Operative ascends — <span class="hl">LV ${next.currentLevel}</span>`);

  const prevByType = Object.fromEntries(prev.stats.map(s => [s.statType, s]));
  next.stats.forEach(s => {
    const p = prevByType[s.statType]; if (!p) return;
    if (s.currentLevel > p.currentLevel)
      pushLog('level', `<span class="c-${s.statType.toLowerCase()}">${s.statType}</span> levels up — <span class="hl">LV ${s.currentLevel}</span>`);
    if ((p.status || '').toLowerCase() === 'rusty' && (s.status || '').toLowerCase() === 'active')
      pushLog('system', `<span class="c-${s.statType.toLowerCase()}">${s.statType}</span> shakes off the rust — Active again`);
  });
}

async function abandonRun() {
  if (!state.char) return;
  if (!confirm('Abandon this run? Your operative, all attributes, and logs will be permanently deleted.')) return;
  try {
    await API.remove(state.char.id);
  } catch (e) {
    if (e.status !== 404) { handleError(e, 'abandon run'); return; }
  }
  localStorage.removeItem(LS.log(state.char.id));
  localStorage.removeItem(LS.done(state.char.id));
  localStorage.removeItem(LS.charId);
  localStorage.removeItem(LS.token);
  state.char = null;
  openOverlay();
}

async function logoutUser() {
  if (!confirm('Log out? You will need to sign back in to continue.')) return;
  const btn = $('#logoutBtn'); btn.disabled = true;
  try {
    await API.logout();
  } catch (_) {
    // Best-effort: proceed with local logout even if the server call failed
    // (e.g. the token had already expired or the API is unreachable).
  }
  if (state.char) {
    localStorage.removeItem(LS.log(state.char.id));
    localStorage.removeItem(LS.done(state.char.id));
  }
  localStorage.removeItem(LS.charId);
  localStorage.removeItem(LS.token);
  localStorage.removeItem(LS.isAdmin);
  applyAdminLinkVisibility();
  state.char = null;
  btn.disabled = false;
  $('#app').hidden = true;
  openOverlay('login');
}

function handleError(e, action) {
  const isNetwork = e instanceof TypeError;
  setConn(false);
  if (isNetwork) {
    const label = apiBase() || 'same origin';
    showBanner(`<b>Connection severed.</b> Could not reach the API at <b>${esc(label)}</b>. Make sure the Spring Boot app is running, then reload the page.`);
    pushLog('error', `Connection severed while trying to ${esc(action)} — API unreachable at ${esc(label)}`);
  } else {
    showBanner(`<b>Request failed.</b> ${esc(e.message)} (while trying to ${esc(action)}).`);
    pushLog('error', `Failed to ${esc(action)}: ${esc(e.message)}`);
  }
}

/* ========================== overlay ======================================= */
/* 'register' (new account + name an operative), 'login' (existing account),
   or 'create' (post-login: account has no operative yet, name one). */
let overlayMode = 'register';
let forgotUsername = '';

function applyOverlayMode() {
  const isRegister = overlayMode === 'register';
  const isCreate    = overlayMode === 'create';
  const isLogin     = overlayMode === 'login';
  const isForgot    = overlayMode === 'forgot';
  const isForgot2   = overlayMode === 'forgot2';

  $('#ovNameField').hidden           = !(isRegister || isCreate);
  $('#ovUsernameField').hidden       = isCreate || isForgot2;
  $('#ovPasswordField').hidden       = !(isRegister || isLogin);
  $('#ovQuestionField').hidden       = !isRegister;
  $('#ovAnswerField').hidden         = !(isRegister || isForgot2);
  $('#ovForgotQuestionField').hidden = !isForgot2;
  $('#ovNewPasswordField').hidden    = !isForgot2;
  $('#ovToggleWrap').hidden          = isCreate;
  $('#ovForgotLinkWrap').hidden      = !isLogin;
  $('#ovAnswerLabel').textContent    = isForgot2 ? 'Your Answer' : 'Security Answer';

  if (isRegister) {
    $('#ovTitle').textContent  = 'Bind Your Soul';
    $('#ovSub').textContent    = 'Register an account and forge a new operative. The Iron Path remembers nothing of the idle.';
    $('#ovSubmit').textContent = 'Bind Soul';
    $('#ovToggleMode').textContent = 'Already bound? Log in instead.';
  } else if (isCreate) {
    $('#ovTitle').textContent  = 'Name Your Operative';
    $('#ovSub').textContent    = "You're signed in, but this account has no operative yet.";
    $('#ovSubmit').textContent = 'Begin';
  } else if (isForgot) {
    $('#ovTitle').textContent  = 'Recover Access';
    $('#ovSub').textContent    = 'Enter your username to retrieve your security question.';
    $('#ovSubmit').textContent = 'Continue';
    $('#ovToggleMode').textContent = '← Back to login';
  } else if (isForgot2) {
    $('#ovTitle').textContent  = 'Answer & Reset';
    $('#ovSub').textContent    = 'Answer your security question to set a new password.';
    $('#ovSubmit').textContent = 'Reset Password';
    $('#ovToggleMode').textContent = '← Back to login';
  } else {
    $('#ovTitle').textContent  = 'Reconnect';
    $('#ovSub').textContent    = 'Sign back in to resume your run.';
    $('#ovSubmit').textContent = 'Log In';
    $('#ovToggleMode').textContent = "Don't have an account? Register instead.";
  }
}

function openOverlay(mode = 'register') {
  overlayMode = mode;
  $('#ovErr').textContent = '';
  $('#ovUsername').value = mode === 'forgot2' ? forgotUsername : '';
  $('#ovPassword').value = '';
  $('#ovName').value     = '';
  $('#ovQuestion').value = '';
  $('#ovAnswer').value   = '';
  $('#ovNewPassword').value = '';
  if (mode !== 'forgot2') forgotUsername = '';
  applyOverlayMode();
  $('#overlay').classList.add('show');
  (mode === 'create' ? $('#ovName') : mode === 'forgot2' ? $('#ovAnswer') : $('#ovUsername')).focus();
}
function closeOverlay() { $('#overlay').classList.remove('show'); }
function toggleOverlayMode() {
  if (overlayMode === 'forgot' || overlayMode === 'forgot2') { openOverlay('login'); return; }
  openOverlay(overlayMode === 'register' ? 'login' : 'register');
}

async function enterAppWithNewCharacter(name) {
  const created = await API.create(name);
  localStorage.setItem(LS.charId, created.id);
  state.char = created;
  saveLog(created.id, [{ category: 'system', html: `SYSTEM: Soul bound to <span class="hl">${esc(created.characterName)}</span>. Welcome to the Iron Path. Inactivity has consequences.` }]);
  await loadQuests(created.currentLevel);
  await loadProgress();
  await loadDailyQuest();
  await loadActiveEvents();
  await refreshAchievementCatalog();
  setConn(true); hideBanner();
  closeOverlay();
  $('#app').hidden = false;
  render();
}

async function enterAppAfterLogin() {
  try {
    const char = await API.getMine();
    localStorage.setItem(LS.charId, char.id);
    state.char = char;
    if (loadLog(char.id).length === 0)
      saveLog(char.id, [{ category: 'system', html: `SYSTEM: Soul-link re-established with <span class="hl">${esc(char.characterName)}</span>.` }]);
    await loadQuests(char.currentLevel);
    await loadProgress();
    await loadDailyQuest();
    await loadActiveEvents();
    await refreshAchievementCatalog();
    setConn(true); hideBanner();
    closeOverlay();
    $('#app').hidden = false;
    render();
  } catch (e) {
    if (e.status === 404) { openOverlay('create'); return; }
    throw e;
  }
}

async function submitOverlay() {
  const btn = $('#ovSubmit'); btn.disabled = true;
  $('#ovErr').textContent = '';
  try {
    if (overlayMode === 'create') {
      const name = $('#ovName').value.trim();
      if (!name) { $('#ovErr').textContent = 'Give your operative a name.'; return; }
      await enterAppWithNewCharacter(name);
      return;
    }

    if (overlayMode === 'forgot') {
      const username = $('#ovUsername').value.trim();
      if (!username || username.length < 3) { $('#ovErr').textContent = 'Username must be at least 3 characters.'; return; }
      const res = await API.forgotPassword(username);
      forgotUsername = username;
      $('#ovForgotQuestionText').textContent = res.question;
      openOverlay('forgot2');
      return;
    }

    if (overlayMode === 'forgot2') {
      const answer = $('#ovAnswer').value.trim();
      const newPassword = $('#ovNewPassword').value;
      if (!answer) { $('#ovErr').textContent = 'Enter your answer.'; return; }
      if (!newPassword || newPassword.length < 8) { $('#ovErr').textContent = 'New password must be at least 8 characters.'; return; }
      const auth = await API.resetPassword(forgotUsername, answer, newPassword);
      localStorage.setItem(LS.token, auth.token);
      cacheAdminFlag(auth.isAdmin);
      await enterAppAfterLogin();
      return;
    }

    const username = $('#ovUsername').value.trim();
    const password = $('#ovPassword').value;
    if (!username || username.length < 3) { $('#ovErr').textContent = 'Username must be at least 3 characters.'; return; }
    if (!password || (overlayMode === 'register' && password.length < 8)) { $('#ovErr').textContent = 'Password must be at least 8 characters.'; return; }

    if (overlayMode === 'register') {
      const name = $('#ovName').value.trim();
      const question = $('#ovQuestion').value.trim();
      const answer = $('#ovAnswer').value.trim();
      if (!name) { $('#ovErr').textContent = 'Give your operative a name.'; return; }
      if (!question) { $('#ovErr').textContent = 'Set a security question for password recovery.'; return; }
      if (!answer) { $('#ovErr').textContent = 'Set an answer to your security question.'; return; }
      const auth = await API.register(username, password, question, answer);
      localStorage.setItem(LS.token, auth.token);
      cacheAdminFlag(auth.isAdmin);
      await enterAppWithNewCharacter(name);
    } else {
      const auth = await API.login(username, password);
      localStorage.setItem(LS.token, auth.token);
      cacheAdminFlag(auth.isAdmin);
      await enterAppAfterLogin();
    }
  } catch (e) {
    const isNetwork = e instanceof TypeError;
    $('#ovErr').textContent = isNetwork
      ? `Cannot reach the API at ${apiBase() || 'same origin'}. Is the server running?`
      : (e.status === 409 ? 'That username is already taken.'
        : e.status === 401 ? (overlayMode === 'forgot2' ? 'Incorrect answer.' : 'Invalid username or password.')
        : e.status === 404 ? 'No recovery question found for that username.'
        : e.message);
    setConn(false);
  } finally {
    btn.disabled = false;
  }
}

/* ========================== account security ================================ */
async function openAccountOverlay() {
  $('#acctErr').textContent = '';
  $('#acctCurrentPassword').value = '';
  $('#acctQuestion').value = '';
  $('#acctAnswer').value = '';
  $('#acctApiBase').value = API_BASE;
  $('#accountOverlay').classList.add('show');
  try {
    const res = await API.getSecurityQuestion();
    $('#acctSub').textContent = res.question
      ? `Current question: "${res.question}" — enter your password to change it.`
      : 'No recovery question set yet — set one so you can reset your password if you forget it.';
  } catch (_) {
    $('#acctSub').textContent = 'Set or update your password-recovery question.';
  }
}
function closeAccountOverlay() { $('#accountOverlay').classList.remove('show'); }

async function submitAccountOverlay() {
  const btn = $('#acctSubmit'); btn.disabled = true;
  $('#acctErr').textContent = '';
  try {
    const currentPassword = $('#acctCurrentPassword').value;
    const question = $('#acctQuestion').value.trim();
    const answer = $('#acctAnswer').value.trim();
    if (!currentPassword) { $('#acctErr').textContent = 'Enter your current password.'; return; }
    if (!question) { $('#acctErr').textContent = 'Enter a security question.'; return; }
    if (!answer) { $('#acctErr').textContent = 'Enter an answer.'; return; }
    await API.setSecurityQuestion(currentPassword, question, answer);
    closeAccountOverlay();
    pushLog('system', 'SYSTEM: Recovery question updated.');
  } catch (e) {
    $('#acctErr').textContent = e.status === 401 ? 'Incorrect password.' : e.message;
  } finally {
    btn.disabled = false;
  }
}

function saveApiBaseFromAccount() {
  setApiBase($('#acctApiBase').value);
  $('#acctApiBase').value = API_BASE;
  setConn(true);
  pushLog('system', `SYSTEM: API base updated to <span class="hl">${esc(API_BASE || 'same origin')}</span>. Reload to reconnect.`);
}

/* ========================== badges ========================================= */
let achievementCatalog = []; // cached full catalog (locked + unlocked); also feeds the title picker
function titleNameFor(code) {
  const a = achievementCatalog.find(x => x.code === code);
  return a ? a.name : code;
}
async function refreshAchievementCatalog() {
  try { achievementCatalog = await API.achievements(state.char.id); } catch (_) { achievementCatalog = []; }
}

async function openBadges() {
  $('#badgesErr').textContent = '';
  $('#badgeGrid').innerHTML = '<div class="friend-empty">Loading…</div>';
  $('#badgesOverlay').classList.add('show');
  try {
    await refreshAchievementCatalog();
    renderBadgeGrid(achievementCatalog);
  } catch (e) {
    $('#badgeGrid').innerHTML = '';
    $('#badgesErr').textContent = e.message || 'Failed to load badges.';
  }
}
function closeBadges() { $('#badgesOverlay').classList.remove('show'); }

function renderBadgeGrid(badges) {
  $('#badgeGrid').innerHTML = badges.map(b => `
    <div class="badge-tile ${b.unlocked ? 'unlocked' : 'locked'}">
      <div class="badge-tile__icon">${b.unlocked ? esc(b.icon) : '❔'}</div>
      <div class="badge-tile__name">${esc(b.name)}</div>
      <div class="badge-tile__desc">${esc(b.description)}</div>
      ${b.unlocked ? `<div class="badge-tile__date">${esc((b.unlockedAt || '').slice(0, 10))}</div>` : ''}
    </div>`).join('');
}

/* Toast shown when a claim unlocks one or more badges — result.newAchievements
   from the claim response, rendered without a round trip to the catalog. */
function showAchievementToasts(achievements) {
  const stack = $('#toastStack');
  achievements.forEach((a, i) => {
    setTimeout(() => {
      const el = document.createElement('div');
      el.className = 'toast';
      el.innerHTML = `
        <span class="toast__icon">${esc(a.icon)}</span>
        <span>
          <div class="toast__title">Badge Unlocked</div>
          <div class="toast__name">${esc(a.name)}</div>
          <div class="toast__desc">${esc(a.description)}</div>
        </span>`;
      stack.appendChild(el);
      setTimeout(() => el.remove(), 5100);
    }, i * 300);
  });
}

/* ========================== timed events ===================================== */
let activeEvents = [];

async function loadActiveEvents() {
  try { activeEvents = await API.activeEvents(); } catch (_) { activeEvents = []; }
}

/** Combined multiplier from every currently-active event applicable to a stat (1 if none). */
function eventMultiplierFor(statType) {
  return activeEvents
    .filter(e => !e.appliesToStat || e.appliesToStat === statType)
    .reduce((m, e) => m * Number(e.xpMultiplier), 1);
}

function renderEventBanner() {
  const el = $('#eventBanner');
  if (activeEvents.length === 0) { el.hidden = true; return; }
  el.hidden = false;
  el.innerHTML = activeEvents.map(e => {
    const scope = e.appliesToStat ? ` (${e.appliesToStat} only)` : '';
    return `<span><span class="event-banner__glyph">✨</span> <b>${esc(e.name)}</b> — ${Number(e.xpMultiplier).toFixed(2)}x XP${scope}</span>`;
  }).join(' &nbsp;·&nbsp; ');
}

/* ========================== quest submission ================================= */
function openSubmitQuest() {
  $('#submitQuestErr').textContent = '';
  $('#sq-title').value = '';
  $('#sq-desc').value = '';
  $('#sq-stat').value = 'STR';
  $('#sq-tag').value = '';
  $('#sq-minlevel').value = '1';
  $('#sq-minutes').value = '';
  $('#submitQuestOverlay').classList.add('show');
}
function closeSubmitQuest() { $('#submitQuestOverlay').classList.remove('show'); }

async function submitQuestIdea() {
  const btn = $('#submitQuestSaveBtn'); btn.disabled = true;
  $('#submitQuestErr').textContent = '';
  try {
    const title = $('#sq-title').value.trim();
    if (!title) { $('#submitQuestErr').textContent = 'Give your quest a title.'; return; }
    await API.submitQuest({
      title,
      description: $('#sq-desc').value.trim(),
      targetStat: $('#sq-stat').value,
      tag: $('#sq-tag').value || null,
      minLevel: parseInt($('#sq-minlevel').value, 10) || 1,
      estimatedMinutes: $('#sq-minutes').value ? parseInt($('#sq-minutes').value, 10) : null,
    });
    closeSubmitQuest();
    pushLog('system', `SYSTEM: Quest idea "<span class="hl">${esc(title)}</span>" sent for admin review.`);
  } catch (e) {
    $('#submitQuestErr').textContent = e.message || 'Failed to submit quest.';
  } finally {
    btn.disabled = false;
  }
}

/* ========================== customization =================================== */
let customizeSelectedAvatar = null;

async function openCustomize() {
  $('#customizeErr').textContent = '';
  customizeSelectedAvatar = state.char.avatarId;
  renderAvatarGrid();
  $('#customizeOverlay').classList.add('show');
  await refreshAchievementCatalog();
  const unlocked = achievementCatalog.filter(a => a.unlocked);
  $('#titleSelect').innerHTML = '<option value="">— none —</option>'
    + unlocked.map(a => `<option value="${esc(a.code)}">${esc(a.icon)} ${esc(a.name)}</option>`).join('');
  $('#titleSelect').value = state.char.titleAchievementCode || '';
}
function closeCustomize() { $('#customizeOverlay').classList.remove('show'); }

function renderAvatarGrid() {
  const grid = $('#avatarGrid');
  grid.innerHTML = Object.keys(AVATAR_EMOJI).map(id =>
    `<div class="avatar-choice ${id === customizeSelectedAvatar ? 'selected' : ''}" data-avatar="${id}" title="${id}">${AVATAR_EMOJI[id]}</div>`
  ).join('');
  grid.querySelectorAll('.avatar-choice').forEach(el => {
    el.addEventListener('click', () => { customizeSelectedAvatar = el.dataset.avatar; renderAvatarGrid(); });
  });
}

async function saveCustomization() {
  const btn = $('#customizeSaveBtn'); btn.disabled = true;
  $('#customizeErr').textContent = '';
  try {
    const titleCode = $('#titleSelect').value || null;
    const updated = await API.setCustomization(state.char.id, customizeSelectedAvatar, titleCode);
    state.char = updated;
    closeCustomize();
    renderHud();
    pushLog('system', 'SYSTEM: Appearance updated.');
  } catch (e) {
    $('#customizeErr').textContent = e.message || 'Failed to save customization.';
  } finally {
    btn.disabled = false;
  }
}

/* ========================== leaderboard ===================================== */
const LB_METRIC_LABEL = { level: 'LV', xp: 'XP', streak: 'DAY' };
let lbState = { scope: 'global', metric: 'level' };

function openLeaderboard() {
  $('#leaderboardOverlay').classList.add('show');
  loadLeaderboard();
}
function closeLeaderboard() { $('#leaderboardOverlay').classList.remove('show'); }

async function loadLeaderboard() {
  $('#leaderboardErr').textContent = '';
  $('#leaderboardBody').innerHTML = '<div class="friend-empty">Loading…</div>';
  try {
    const rows = await API.leaderboard(lbState.scope, lbState.metric);
    renderLeaderboard(rows);
  } catch (e) {
    $('#leaderboardBody').innerHTML = '';
    $('#leaderboardErr').textContent = e.message || 'Failed to load rankings.';
  }
}

function renderLeaderboard(rows) {
  const body = $('#leaderboardBody');
  if (rows.length === 0) {
    body.innerHTML = '<div class="friend-empty">No ranked operatives yet.</div>';
    return;
  }
  const unit = LB_METRIC_LABEL[lbState.metric] || '';
  body.innerHTML = rows.map(r => `
    <div class="frow" style="cursor:default;">
      <span class="frow__meta" style="width:28px; flex:none;">#${r.rank}</span>
      <span class="frow__name" style="cursor:default; flex:1;">${esc(r.characterName)} <span class="frow__meta">@${esc(r.username)}</span></span>
      <span class="frow__meta">${r.value} ${unit}</span>
    </div>`).join('');
}

function switchLeaderboardScope(scope) {
  lbState.scope = scope;
  $('#lbScopeTabs').querySelectorAll('.ftab').forEach(b => b.classList.toggle('active', b.dataset.scope === scope));
  loadLeaderboard();
}
function switchLeaderboardMetric(metric) {
  lbState.metric = metric;
  $('#lbMetricTabs').querySelectorAll('.ftab').forEach(b => b.classList.toggle('active', b.dataset.metric === metric));
  loadLeaderboard();
}

/* ========================== friends ======================================== */
const VIS_LABEL = { NONE: 'Hidden', BASIC: 'Basic', FULL: 'Full' };
let friendsState = { tab: 'list', friends: [], incoming: [], outgoing: [], feed: [], defaultVisibility: 'BASIC', detail: null };

function friendsErr(msg) { $('#friendsErr').textContent = msg || ''; }

function openFriends() {
  friendsState.tab = 'list';
  $('#friendsOverlay').classList.add('show');
  switchFriendsTab('list');
  loadFriendsData();
}
function closeFriends() { $('#friendsOverlay').classList.remove('show'); }

function switchFriendsTab(tab) {
  friendsState.tab = tab;
  friendsErr('');
  ['list', 'feed', 'requests', 'settings', 'detail'].forEach(t => {
    $('#ftab' + t[0].toUpperCase() + t.slice(1)).hidden = t !== tab;
  });
  $('#friendsTabs').querySelectorAll('.ftab').forEach(b => b.classList.toggle('active', b.dataset.tab === tab));
}

async function loadFriendsData() {
  try {
    const [friends, incoming, outgoing, settings, feed] = await Promise.all([
      API.friends(), API.incomingRequests(), API.outgoingRequests(), API.getFriendSettings(), API.friendFeed(),
    ]);
    friendsState.friends = friends;
    friendsState.incoming = incoming;
    friendsState.outgoing = outgoing;
    friendsState.defaultVisibility = settings.defaultVisibility;
    friendsState.feed = feed;
    renderFriendsList();
    renderFriendRequests();
    renderFriendSettings();
    renderFriendFeed();
  } catch (e) {
    friendsErr(e.message || 'Failed to load allies.');
  }
}

function timeAgo(iso) {
  const diffMs = Date.now() - new Date(iso).getTime();
  const mins = Math.round(diffMs / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.round(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return `${Math.round(hrs / 24)}d ago`;
}

function renderFriendFeed() {
  const body = $('#friendFeedBody');
  if (friendsState.feed.length === 0) {
    body.innerHTML = '<div class="friend-empty">No recent activity from your allies.</div>';
    return;
  }
  body.innerHTML = friendsState.feed.map(entry => {
    const detail = entry.questTitle
      ? `cleared <span class="hl">${esc(entry.questTitle)}</span>${entry.xpEarned != null ? ` <span style="color:var(--faint)">(+${entry.xpEarned} XP${entry.statType ? ' · ' + entry.statType : ''})</span>` : ''}`
      : 'completed a quest';
    return `<div class="frow" style="cursor:default;">
      <span class="frow__name" style="cursor:default; flex:none; font-weight:600;">${esc(entry.username)}</span>
      <span class="frow__meta" style="flex:1;">${detail}</span>
      <span class="frow__meta">${timeAgo(entry.loggedAt)}</span>
    </div>`;
  }).join('');
}

function renderFriendsList() {
  const body = $('#friendsListBody');
  if (friendsState.friends.length === 0) {
    body.innerHTML = '<div class="friend-empty">No allies yet — send a request above.</div>';
    return;
  }
  body.innerHTML = friendsState.friends.map(f => `
    <div class="frow" data-id="${f.friendshipId}">
      <button class="frow__name" data-action="view">${esc(f.username)}</button>
      <select data-action="visibility" title="What ${esc(f.username)} can see about you">
        ${['NONE', 'BASIC', 'FULL'].map(v => `<option value="${v}" ${f.visibilityGranted === v ? 'selected' : ''}>${VIS_LABEL[v]}</option>`).join('')}
      </select>
      <button class="frow-btn" data-action="remove">Remove</button>
    </div>`).join('');
}

function renderFriendRequests() {
  const inBody = $('#incomingListBody');
  inBody.innerHTML = friendsState.incoming.length === 0
    ? '<div class="friend-empty">No incoming requests.</div>'
    : friendsState.incoming.map(f => `
      <div class="frow" data-id="${f.friendshipId}">
        <span class="frow__name" style="cursor:default;">${esc(f.username)}</span>
        <button class="frow-btn accept" data-action="accept">Accept</button>
        <button class="frow-btn" data-action="decline">Decline</button>
      </div>`).join('');

  const outBody = $('#outgoingListBody');
  outBody.innerHTML = friendsState.outgoing.length === 0
    ? '<div class="friend-empty">No sent requests pending.</div>'
    : friendsState.outgoing.map(f => `
      <div class="frow" data-id="${f.friendshipId}">
        <span class="frow__name" style="cursor:default;">${esc(f.username)}</span>
        <span class="frow__meta">awaiting response</span>
      </div>`).join('');
}

function renderFriendSettings() {
  $('#defaultVisibilitySelect').value = friendsState.defaultVisibility;
}

async function viewFriendDetail(friendshipId) {
  friendsErr('');
  const body = $('#friendDetailBody');
  body.innerHTML = '<div class="friend-empty">Loading…</div>';
  switchFriendsTab('detail');
  try {
    const detail = await API.friendDetail(friendshipId);
    friendsState.detail = detail;
    renderFriendDetail(detail);
  } catch (e) {
    body.innerHTML = '';
    friendsErr(e.message || 'Failed to load ally details.');
  }
}

function renderFriendDetail(d) {
  const body = $('#friendDetailBody');
  const c = d.character;
  if (!c) {
    body.innerHTML = `
      <div class="fdetail__head"><span class="fdetail__name">${esc(d.username)}</span></div>
      <div class="fdetail__hidden">${esc(d.username)} hasn't shared any character details with you.</div>`;
    return;
  }

  const isFull = d.visibility === 'FULL' && c.stats;
  body.innerHTML = `
    <div class="fdetail__head">
      <span class="fdetail__name">${esc(c.characterName)}</span>
      <span class="fdetail__lv">LV ${c.currentLevel}</span>
    </div>
    ${isFull ? `
      <div class="hud__xp" style="margin-top:0;">
        <div class="xp-row"><span>Character XP</span><span class="xp-val">${c.overallXp} / ${c.xpForNextLevel}</span></div>
        <div class="bar"><div class="bar__fill" style="width:${pct(c.overallXp, c.xpForNextLevel)}%"></div></div>
      </div>
      <p style="font-size:13px;color:var(--dim);margin:14px 0 12px;">🔥 Streak: <span style="color:var(--brass-bright);">DAY ${c.streakCount}</span></p>
      <div>${c.stats.map(s => {
        const rusty = (s.status || '').toLowerCase() === 'rusty';
        const lc = s.statType.toLowerCase();
        return `
          <div class="stat">
            <div class="stat__head">
              <span class="stat__tag c-${lc}">${s.statType}</span>
              <span class="stat__name">${esc(STAT_NAME[s.statType] || s.statType)}</span>
              <span class="stat__lv">LV ${s.currentLevel}</span>
              <span class="stat__status ${rusty ? 'is-rusty' : 'is-active'}">${rusty ? 'Rusty' : 'Active'}</span>
            </div>
            <div class="bar stat__bar"><div class="bar__fill fill-${lc}" style="width:${pct(s.currentXp, s.xpForNextLevel)}%"></div></div>
            <div class="stat__xp">${s.currentXp} / ${s.xpForNextLevel} XP</div>
          </div>`;
      }).join('')}</div>
    ` : ''}`;
}

async function sendFriendRequest() {
  const input = $('#addFriendInput');
  const username = input.value.trim();
  friendsErr('');
  if (!username) { friendsErr('Enter a username.'); return; }
  const btn = $('#addFriendBtn'); btn.disabled = true;
  try {
    await API.sendFriendRequest(username);
    input.value = '';
    await loadFriendsData();
  } catch (e) {
    friendsErr(e.status === 404 ? 'No operative found with that username.' : (e.message || 'Failed to send request.'));
  } finally {
    btn.disabled = false;
  }
}

async function handleFriendsClick(e) {
  const tabBtn = e.target.closest('.ftab');
  if (tabBtn) { switchFriendsTab(tabBtn.dataset.tab); return; }

  const row = e.target.closest('.frow');
  const action = e.target.closest('[data-action]')?.dataset.action;
  if (!row || !action) return;
  const id = row.dataset.id;

  try {
    if (action === 'view') await viewFriendDetail(id);
    else if (action === 'remove') {
      if (!confirm('Remove this ally?')) return;
      await API.removeFriend(id);
      await loadFriendsData();
    } else if (action === 'accept') {
      await API.acceptFriendRequest(id);
      await loadFriendsData();
    } else if (action === 'decline') {
      await API.declineFriendRequest(id);
      await loadFriendsData();
    }
  } catch (e2) {
    friendsErr(e2.message || `Failed to ${action}.`);
  }
}

async function handleFriendsChange(e) {
  const select = e.target.closest('[data-action="visibility"]');
  if (select) {
    const id = select.closest('.frow').dataset.id;
    try {
      await API.setFriendVisibility(id, select.value);
    } catch (e2) {
      friendsErr(e2.message || 'Failed to update visibility.');
      await loadFriendsData();
    }
    return;
  }
  if (e.target.id === 'defaultVisibilitySelect') {
    try {
      await API.setFriendSettings(e.target.value);
      friendsState.defaultVisibility = e.target.value;
    } catch (e2) {
      friendsErr(e2.message || 'Failed to update default visibility.');
      renderFriendSettings();
    }
  }
}

/* ========================== boot ========================================== */
async function boot() {
  // Browsers require a user gesture before audio can play; unlock the
  // AudioContext on the very first click anywhere so later async cues
  // (which fire after an awaited API call, outside the original gesture)
  // aren't silently blocked.
  document.addEventListener('click', () => getAudioCtx(), { once: true });

  $('#abandonBtn').addEventListener('click', abandonRun);
  $('#logoutBtn').addEventListener('click', logoutUser);
  $('#ovSubmit').addEventListener('click',   submitOverlay);
  $('#overlay').addEventListener('keydown', e => {
    if (e.key === 'Enter') submitOverlay();
  });
  $('#ovToggleMode').addEventListener('click', e => { e.preventDefault(); toggleOverlayMode(); });
  $('#ovForgotLink').addEventListener('click', e => { e.preventDefault(); openOverlay('forgot'); });

  $('#openBadgesBtn').addEventListener('click', openBadges);
  $('#badgesCloseBtn').addEventListener('click', closeBadges);
  $('#badgesOverlay').addEventListener('click', e => { if (e.target.id === 'badgesOverlay') closeBadges(); });
  $('#openCustomizeBtn').addEventListener('click', openCustomize);
  $('#customizeCloseBtn').addEventListener('click', closeCustomize);
  $('#customizeOverlay').addEventListener('click', e => { if (e.target.id === 'customizeOverlay') closeCustomize(); });
  $('#customizeSaveBtn').addEventListener('click', saveCustomization);
  $('#openSubmitQuestBtn').addEventListener('click', openSubmitQuest);
  $('#submitQuestCloseBtn').addEventListener('click', closeSubmitQuest);
  $('#submitQuestOverlay').addEventListener('click', e => { if (e.target.id === 'submitQuestOverlay') closeSubmitQuest(); });
  $('#submitQuestSaveBtn').addEventListener('click', submitQuestIdea);
  $('#openLeaderboardBtn').addEventListener('click', openLeaderboard);
  $('#leaderboardCloseBtn').addEventListener('click', closeLeaderboard);
  $('#leaderboardOverlay').addEventListener('click', e => { if (e.target.id === 'leaderboardOverlay') closeLeaderboard(); });
  $('#lbScopeTabs').querySelectorAll('.ftab').forEach(b => b.addEventListener('click', () => switchLeaderboardScope(b.dataset.scope)));
  $('#lbMetricTabs').querySelectorAll('.ftab').forEach(b => b.addEventListener('click', () => switchLeaderboardMetric(b.dataset.metric)));
  $('#openFriendsBtn').addEventListener('click', openFriends);
  $('#openAccountBtn').addEventListener('click', openAccountOverlay);
  $('#accountCloseBtn').addEventListener('click', closeAccountOverlay);
  $('#accountOverlay').addEventListener('click', e => { if (e.target.id === 'accountOverlay') closeAccountOverlay(); });
  $('#acctSubmit').addEventListener('click', submitAccountOverlay);
  $('#acctApiBaseSave').addEventListener('click', saveApiBaseFromAccount);
  $('#friendsCloseBtn').addEventListener('click', closeFriends);
  $('#friendsOverlay').addEventListener('click', e => { if (e.target.id === 'friendsOverlay') closeFriends(); });
  $('#friendDetailBack').addEventListener('click', () => switchFriendsTab('list'));
  $('#addFriendBtn').addEventListener('click', sendFriendRequest);
  $('#addFriendInput').addEventListener('keydown', e => { if (e.key === 'Enter') sendFriendRequest(); });
  $('#friendsOverlay').addEventListener('click', handleFriendsClick);
  $('#friendsOverlay').addEventListener('change', handleFriendsChange);

  applyAdminLinkVisibility();

  const id    = localStorage.getItem(LS.charId);
  const token = localStorage.getItem(LS.token);
  if (!id || !token) { openOverlay(); return; }

  try {
    state.char = await API.get(id);
    await loadQuests(state.char.currentLevel);
    await loadProgress();
    await loadDailyQuest();
    await loadActiveEvents();
    await refreshAdminFlag();
    await refreshAchievementCatalog();
    setConn(true);
    $('#app').hidden = false;
    if (loadLog(id).length === 0)
      saveLog(id, [{ category: 'system', html: `SYSTEM: Soul-link re-established with <span class="hl">${esc(state.char.characterName)}</span>.` }]);
    render();
  } catch (e) {
    setConn(false);
    $('#app').hidden = false;
    if (e.status === 404) { localStorage.removeItem(LS.charId); openOverlay(); }
    else if (e.status === 401 || e.status === 403) {
      localStorage.removeItem(LS.charId); localStorage.removeItem(LS.token); localStorage.removeItem(LS.isAdmin);
      applyAdminLinkVisibility();
      openOverlay('login');
      $('#ovErr').textContent = 'Your session expired — log in again to continue.';
    }
    else { handleError(e, 'load your operative'); }
  }
}
boot();

