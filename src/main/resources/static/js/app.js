"use strict";

/* ========================== configuration ================================= */
const LS = {
  token:   'ironpath_token',
  charId:  'ironpath_char_id',
  isAdmin: 'ironpath_is_admin',
  log:     id => `ironpath_log_${id}`,
  done:    id => `ironpath_done_${id}`,
};
const API_BASE = 'http://localhost:8080';
const STAT_ORDER  = ['STR', 'DEX', 'CON', 'WIL'];
const STAT_NAME   = { STR: 'Strength', DEX: 'Dexterity', CON: 'Constitution', WIL: 'Willpower' };

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
let ui    = { activeQuestId: null, checked: false, busy: false };
let quests = [];

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
  daily:   id    => apiFetch(`/api/character/${id}/daily`),
  quests: level  => apiFetch('/api/quests?level=' + level),
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
    quests = await API.quests(level);
    if (ui.activeQuestId && !quests.find(q => q.questId === ui.activeQuestId)) {
      ui.activeQuestId = null;
    }
  } catch (_) {
    quests = [];
  }
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
function render() { renderHud(); renderStats(); renderQuestBoard(); renderLog(); renderProgress(); }

function renderHud() {
  const c = state.char; if (!c) return;
  $('#charName').textContent  = c.characterName;
  $('#charLevel').textContent = c.currentLevel;
  $('#charXp').textContent    = c.overallXp;
  $('#charXpMax').textContent = c.xpForNextLevel;
  $('#charXpFill').style.width = pct(c.overallXp, c.xpForNextLevel) + '%';
  $('#streakDay').textContent = c.streakCount;
  $('#buffVal').textContent   = multiplierFor(c.streakCount).toFixed(2);

  const riskEl = $('#streakRisk');
  const atRisk = c.streakCount > 0 && c.lastWorkoutDate !== todayStr();
  riskEl.hidden = !atRisk;
  if (atRisk) {
    riskEl.textContent = `Streak open until midnight — one quest keeps DAY ${c.streakCount} going. A missed day halves it, it won't reset to zero.`;
  }
}

function renderStats() {
  const c = state.char; if (!c) return;
  const byType = Object.fromEntries(c.stats.map(s => [s.statType, s]));
  $('#statList').innerHTML = STAT_ORDER.map(type => {
    const s = byType[type]; if (!s) return '';
    const rusty = (s.status || '').toLowerCase() === 'rusty';
    const lc = type.toLowerCase();
    return `
      <div class="stat">
        <div class="stat__head">
          <span class="stat__tag c-${lc}">${type}</span>
          <span class="stat__name">${esc(STAT_NAME[type] || type)}</span>
          <span class="stat__lv">LV ${s.currentLevel}</span>
          <span class="stat__status ${rusty ? 'is-rusty' : 'is-active'}">${rusty ? 'Rusty' : 'Active'}</span>
        </div>
        <div class="bar stat__bar"><div class="bar__fill fill-${lc}" style="width:${pct(s.currentXp, s.xpForNextLevel)}%"></div></div>
        <div class="stat__xp">${s.currentXp} / ${s.xpForNextLevel} XP</div>
      </div>`;
  }).join('');
}

function renderQuestBoard() {
  const c = state.char;
  if (!c || quests.length === 0) {
    $('#questList').innerHTML = '<p class="quest-empty">No quests available — check API connection.</p>';
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
    const estStat  = Math.round(q.baseStatXp      * scale * mult * dailyMult);
    const estChar  = Math.round(q.baseCharacterXp * scale * mult * dailyMult);

    return `
      <div class="qcard ${isActive ? 'is-active' : ''} ${cleared ? 'is-cleared' : ''} ${isDaily ? 'is-daily' : ''}" data-qid="${esc(q.questId)}">
        <div class="qcard__header">
          <span class="qcard__rank c-${lc}">${rank}·${q.targetStat}</span>
          ${isDaily ? '<span class="daily-badge" title="Bonus XP for completing today\'s Daily Focus">★ Daily Focus</span>' : ''}
          <span class="qcard__title">${esc(q.title)}</span>
          <span class="qcard__meta">~${estStat+estChar} XP</span>
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
            <div class="rewards__label">Rewards · ${mult.toFixed(2)}x streak · ${scale.toFixed(1)}x level${isDaily ? ` · ${dailyMult.toFixed(2)}x daily focus` : ''}</div>
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
  txt.textContent = ok ? 'linked · ' + apiBase().replace(/^https?:\/\//, '') : 'offline';
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

    // re-fetch quests if the character leveled up (new tiers may unlock)
    if (result.character.currentLevel > prev.currentLevel) {
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
    showBanner(`<b>Connection severed.</b> Could not reach the API at <b>${esc(apiBase())}</b>. Make sure the Spring Boot app is running, then reload the page.`);
    pushLog('error', `Connection severed while trying to ${esc(action)} — API unreachable at ${esc(apiBase())}`);
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
      ? `Cannot reach the API at ${apiBase()}. Is the server running?`
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
      <p style="font-size:13px;color:var(--dim);margin:14px 0 12px;">🔥 Streak: <span style="color:var(--amber);">DAY ${c.streakCount}</span></p>
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
  $('#abandonBtn').addEventListener('click', abandonRun);
  $('#logoutBtn').addEventListener('click', logoutUser);
  $('#ovSubmit').addEventListener('click',   submitOverlay);
  $('#overlay').addEventListener('keydown', e => {
    if (e.key === 'Enter') submitOverlay();
  });
  $('#ovToggleMode').addEventListener('click', e => { e.preventDefault(); toggleOverlayMode(); });
  $('#ovForgotLink').addEventListener('click', e => { e.preventDefault(); openOverlay('forgot'); });

  $('#openFriendsBtn').addEventListener('click', openFriends);
  $('#openAccountBtn').addEventListener('click', openAccountOverlay);
  $('#accountCloseBtn').addEventListener('click', closeAccountOverlay);
  $('#accountOverlay').addEventListener('click', e => { if (e.target.id === 'accountOverlay') closeAccountOverlay(); });
  $('#acctSubmit').addEventListener('click', submitAccountOverlay);
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
    await refreshAdminFlag();
    setConn(true);
    $('#app').hidden = false;
    if (loadLog(id).length === 0)
      saveLog(id, [{ category: 'system', html: `SYSTEM: Soul-link re-established with <span class="hl">${esc(state.char.characterName)}</span>.` }]);
    render();
  } catch (e) {
    setConn(false);
    $('#app').hidden = false;
    if (e.status === 404) { localStorage.removeItem(LS.charId); openOverlay(); }
    else if (e.status === 401) {
      localStorage.removeItem(LS.charId); localStorage.removeItem(LS.token); localStorage.removeItem(LS.isAdmin);
      applyAdminLinkVisibility();
      openOverlay('login');
      $('#ovErr').textContent = 'Your session expired — log in again to continue.';
    }
    else { handleError(e, 'load your operative'); }
  }
}
boot();

