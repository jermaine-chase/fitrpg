const CHAR_ID = 'aaaabbbb-cccc-dddd-eeee-ffffffffffff';
const TOKEN = 'mock.jwt.token';

export { CHAR_ID, TOKEN };

export const MOCK_CHARACTER = {
  id: CHAR_ID,
  characterName: 'IronHero',
  currentLevel: 5,
  overallXp: 250,
  xpForNextLevel: 500,
  streakCount: 3,
  streakFreezesAvailable: 1,
  lastWorkoutDate: new Date().toISOString().slice(0, 10),
  createdAt: '2026-06-01T00:00:00',
  avatarId: 'wolf',
  titleAchievementCode: null,
  stats: [
    { statType: 'STR', currentLevel: 3, currentXp: 100, xpForNextLevel: 200, status: 'Active' },
    { statType: 'DEX', currentLevel: 2, currentXp: 50, xpForNextLevel: 150, status: 'Active' },
    { statType: 'CON', currentLevel: 4, currentXp: 180, xpForNextLevel: 250, status: 'Active' },
    { statType: 'WIL', currentLevel: 1, currentXp: 20, xpForNextLevel: 100, status: 'Rusty' },
  ],
};

export const MOCK_QUESTS = [
  {
    questId: 'Q-1001', title: 'Morning 5k Run',
    description: 'Complete a 5 km run before noon.',
    targetStat: 'CON', baseCharacterXp: 60, baseStatXp: 40, minLevel: 1,
    tag: 'CARDIO', estimatedMinutes: 30,
  },
  {
    questId: 'Q-1002', title: 'Weight Training Session',
    description: 'Complete a full-body strength session.',
    targetStat: 'STR', baseCharacterXp: 50, baseStatXp: 50, minLevel: 1,
    tag: 'STRENGTH', estimatedMinutes: 45,
  },
];

export const MOCK_DAILY = { quest: MOCK_QUESTS[1], bonusMultiplier: 1.25 };

export const MOCK_CLAIM_RESULT = {
  character: {
    ...MOCK_CHARACTER,
    overallXp: 310,
    streakCount: 4,
    stats: [
      { statType: 'STR', currentLevel: 3, currentXp: 140, xpForNextLevel: 200, status: 'Active' },
      { statType: 'DEX', currentLevel: 2, currentXp: 50, xpForNextLevel: 150, status: 'Active' },
      { statType: 'CON', currentLevel: 4, currentXp: 230, xpForNextLevel: 250, status: 'Active' },
      { statType: 'WIL', currentLevel: 1, currentXp: 20, xpForNextLevel: 100, status: 'Rusty' },
    ],
  },
  bonusChallenge: null,
  dailyFocusBonusXp: 0,
  newAchievements: [],
};

export const MOCK_LEVEL_UP_CLAIM_RESULT = {
  character: { ...MOCK_CLAIM_RESULT.character, currentLevel: 6 },
  bonusChallenge: null,
  dailyFocusBonusXp: 0,
  newAchievements: [],
};

export const MOCK_HISTORY = [
  { loggedAt: new Date().toISOString(), finalXpAwarded: 60, statType: 'CON', questTitle: 'Morning 5k Run' },
];

export const MOCK_ADMIN_QUESTS = [
  {
    questId: 'Q-1001', title: 'Morning 5k Run', description: 'Complete a 5 km run before noon.',
    targetStat: 'CON', baseCharacterXp: 60, baseStatXp: 40, minLevel: 1, tag: 'CARDIO',
    estimatedMinutes: 30, status: 'APPROVED',
  },
  {
    questId: 'Q-1002', title: 'Weight Training Session', description: 'Complete a full-body strength session.',
    targetStat: 'STR', baseCharacterXp: 50, baseStatXp: 50, minLevel: 1, tag: 'STRENGTH',
    estimatedMinutes: 45, status: 'APPROVED',
  },
];

export const MOCK_PENDING_QUESTS = [
  {
    questId: 'Q-9001', title: 'Ruck March', description: 'Weighted ruck march, 5 miles.',
    targetStat: 'CON', minLevel: 5, baseCharacterXp: 50, baseStatXp: 50,
  },
];

export const MOCK_EVENTS = [
  {
    id: 'evt-1', name: 'Double XP Weekend',
    startAt: '2026-01-01T00:00:00', endAt: '2026-01-03T00:00:00',
    xpMultiplier: 2.0, appliesToStat: null,
  },
];

export const MOCK_ADMIN_CHARACTERS = [
  {
    id: CHAR_ID, characterName: 'IronHero', currentLevel: 5, overallXp: 250, xpForNextLevel: 500,
    streakCount: 3, lastWorkoutDate: '2026-06-21', createdAt: '2026-06-01T00:00:00', stats: [],
  },
  {
    id: '11112222-3333-4444-5555-666677778888', characterName: 'SteelWalker', currentLevel: 2,
    overallXp: 80, xpForNextLevel: 200, streakCount: 0, lastWorkoutDate: null,
    createdAt: '2026-06-10T00:00:00', stats: [],
  },
];

/** Pre-seed localStorage so the app boots straight into the main dashboard. */
export async function seedCharacterStorage(page, { charId = CHAR_ID, token = TOKEN, isAdmin = false } = {}) {
  await page.addInitScript(([id, tok, admin]) => {
    localStorage.setItem('ironpath_char_id', id);
    localStorage.setItem('ironpath_token', tok);
    localStorage.setItem('ironpath_is_admin', admin ? '1' : '0');
  }, [charId, token, isAdmin]);
}

/** Pre-seed localStorage with just a token (admin.html reuses this key). */
export async function seedAdminSession(page, { token = TOKEN, isAdmin = true } = {}) {
  await page.addInitScript(([tok, admin]) => {
    localStorage.setItem('ironpath_token', tok);
    localStorage.setItem('ironpath_is_admin', admin ? '1' : '0');
  }, [token, isAdmin]);
}

/**
 * Intercept every /api/** call index.html makes. Each option can be
 * overridden per-test; handlers return sensible empty defaults for the
 * secondary panels (friends/badges/leaderboard/events) so tests that don't
 * care about them don't need to stub everything individually.
 */
export async function setupIndexApiMocks(page, {
  character = MOCK_CHARACTER,
  quests = MOCK_QUESTS,
  claimResult = MOCK_CLAIM_RESULT,
  history = MOCK_HISTORY,
  daily = MOCK_DAILY,
  isAdmin = false,
  securityQuestion = null,
} = {}) {
  await page.route('**/api/**', async (route) => {
    const req = route.request();
    const url = new URL(req.url());
    const path = url.pathname;
    const method = req.method();
    const body = () => { try { return JSON.parse(req.postData() || '{}'); } catch (_) { return {}; } };

    if (path === '/api/auth/register' && method === 'POST') {
      return route.fulfill({ status: 201, json: { token: TOKEN, isAdmin } });
    }
    if (path === '/api/auth/login' && method === 'POST') {
      return route.fulfill({ json: { token: TOKEN, isAdmin } });
    }
    if (path === '/api/auth/logout' && method === 'POST') {
      return route.fulfill({ status: 204, body: '' });
    }
    if (path === '/api/auth/forgot-password' && method === 'POST') {
      return route.fulfill({ json: { question: 'First pet?' } });
    }
    if (path === '/api/auth/reset-password' && method === 'POST') {
      return route.fulfill({ json: { token: TOKEN, isAdmin } });
    }
    if (path === '/api/auth/security-question' && method === 'GET') {
      return route.fulfill({ json: { question: securityQuestion } });
    }
    if (path === '/api/auth/security-question' && method === 'PUT') {
      return route.fulfill({ json: { question: body().securityQuestion } });
    }
    if (path === '/api/auth/me' && method === 'GET') {
      return route.fulfill({ json: { username: 'ironhero', isAdmin } });
    }
    if (path === '/api/character' && method === 'POST') {
      return route.fulfill({ json: character, status: 201 });
    }
    if (path === '/api/character/mine' && method === 'GET') {
      return route.fulfill({ json: character });
    }
    if (path === `/api/character/${character.id}` && method === 'GET') {
      return route.fulfill({ json: character });
    }
    if (path === `/api/character/${character.id}` && method === 'DELETE') {
      return route.fulfill({ status: 204, body: '' });
    }
    if (path.endsWith('/claim') && method === 'POST') {
      return route.fulfill({ json: claimResult });
    }
    if (path.endsWith('/history') && method === 'GET') {
      return route.fulfill({ json: history });
    }
    if (path.endsWith('/daily') && method === 'GET') {
      return route.fulfill({ json: daily });
    }
    if (path.endsWith('/achievements') && method === 'GET') {
      return route.fulfill({ json: [] });
    }
    if (path.endsWith('/customization') && method === 'PUT') {
      return route.fulfill({ json: { ...character, ...body() } });
    }
    if (path === '/api/quests' && method === 'GET') {
      return route.fulfill({ json: quests });
    }
    if (path === '/api/quests/submit' && method === 'POST') {
      return route.fulfill({ status: 201, json: { ...body(), questId: 'Q-SUBMIT', status: 'PENDING' } });
    }
    if (path === '/api/events/active' && method === 'GET') {
      return route.fulfill({ json: [] });
    }
    if (path === '/api/leaderboard' && method === 'GET') {
      return route.fulfill({ json: [] });
    }
    if (path === '/api/friends' && method === 'GET') {
      return route.fulfill({ json: [] });
    }
    if (path === '/api/friends/requests' && method === 'GET') {
      return route.fulfill({ json: [] });
    }
    if (path === '/api/friends/requests/sent' && method === 'GET') {
      return route.fulfill({ json: [] });
    }
    if (path === '/api/friends/requests' && method === 'POST') {
      return route.fulfill({ status: 404, json: { message: 'No operative found with that username.' } });
    }
    if (path === '/api/friends/feed' && method === 'GET') {
      return route.fulfill({ json: [] });
    }
    if (path === '/api/friends/settings' && method === 'GET') {
      return route.fulfill({ json: { defaultVisibility: 'BASIC' } });
    }

    await route.abort();
  });
}

export async function setupAdminApiMocks(page, {
  quests = MOCK_ADMIN_QUESTS,
  pending = MOCK_PENDING_QUESTS,
  events = MOCK_EVENTS,
  characters = MOCK_ADMIN_CHARACTERS,
  forbidden = false,
} = {}) {
  await page.route('**/api/**', async (route) => {
    const req = route.request();
    const url = new URL(req.url());
    const path = url.pathname;
    const method = req.method();
    const body = () => { try { return JSON.parse(req.postData() || '{}'); } catch (_) { return {}; } };

    if (forbidden && path.startsWith('/api/admin/')) {
      return route.fulfill({ status: 403, json: { message: 'Forbidden' } });
    }

    if (path === '/api/admin/quests' && method === 'GET') {
      return route.fulfill({ json: quests });
    }
    if (path === '/api/admin/quests' && method === 'POST') {
      return route.fulfill({ json: { ...body(), status: 'APPROVED' }, status: 201 });
    }
    if (path.startsWith('/api/admin/quests/pending') && method === 'GET') {
      return route.fulfill({ json: pending });
    }
    if (/\/api\/admin\/quests\/[^/]+\/(approve|reject)$/.test(path) && method === 'POST') {
      return route.fulfill({ status: 204, body: '' });
    }
    if (path.startsWith('/api/admin/quests/') && method === 'PUT') {
      return route.fulfill({ json: { ...body(), questId: decodeURIComponent(path.split('/').pop()) } });
    }
    if (path.startsWith('/api/admin/quests/') && method === 'DELETE') {
      return route.fulfill({ status: 204, body: '' });
    }
    if (path === '/api/admin/events' && method === 'GET') {
      return route.fulfill({ json: events });
    }
    if (path === '/api/admin/events' && method === 'POST') {
      return route.fulfill({ json: { ...body(), id: 'evt-new' }, status: 201 });
    }
    if (path.startsWith('/api/admin/events/') && method === 'PUT') {
      return route.fulfill({ json: { ...body(), id: decodeURIComponent(path.split('/').pop()) } });
    }
    if (path.startsWith('/api/admin/events/') && method === 'DELETE') {
      return route.fulfill({ status: 204, body: '' });
    }
    if (path === '/api/admin/characters' && method === 'GET') {
      return route.fulfill({ json: characters });
    }
    if (path.startsWith('/api/admin/characters/') && method === 'PUT') {
      const id = decodeURIComponent(path.split('/').pop());
      const orig = characters.find(c => c.id === id) || characters[0];
      return route.fulfill({ json: { ...orig, ...body() } });
    }
    if (path.startsWith('/api/admin/characters/') && method === 'DELETE') {
      return route.fulfill({ status: 204, body: '' });
    }

    await route.abort();
  });
}
