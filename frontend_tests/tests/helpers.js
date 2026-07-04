const API_ORIGIN = 'http://localhost:8080';

const CHAR_ID = 'aaaabbbb-cccc-dddd-eeee-ffffffffffff';

export const MOCK_CHARACTER = {
  id: CHAR_ID,
  characterName: 'IronHero',
  currentLevel: 5,
  overallXp: 250,
  xpForNextLevel: 500,
  streakCount: 3,
  lastWorkoutDate: '2026-06-21',
  createdAt: '2026-06-01T00:00:00',
  stats: [
    { statType: 'STR', currentLevel: 3, currentXp: 100, xpForNextLevel: 200, status: 'Active' },
    { statType: 'DEX', currentLevel: 2, currentXp: 50,  xpForNextLevel: 150, status: 'Active' },
    { statType: 'CON', currentLevel: 4, currentXp: 180, xpForNextLevel: 250, status: 'Active' },
    { statType: 'WIL', currentLevel: 1, currentXp: 20,  xpForNextLevel: 100, status: 'Rusty'  },
  ],
};

export const MOCK_QUESTS = [
  {
    questId: 'Q-1001', title: 'Morning 5k Run',
    description: 'Complete a 5 km run before noon.',
    targetStat: 'CON', baseCharacterXp: 60, baseStatXp: 40, minLevel: 1,
  },
  {
    questId: 'Q-1002', title: 'Weight Training Session',
    description: 'Complete a full-body strength session.',
    targetStat: 'STR', baseCharacterXp: 50, baseStatXp: 50, minLevel: 1,
  },
];

export const MOCK_CLAIM_RESULT = {
  character: {
    ...MOCK_CHARACTER,
    overallXp: 310,
    streakCount: 4,
    stats: [
      { statType: 'STR', currentLevel: 3, currentXp: 140, xpForNextLevel: 200, status: 'Active' },
      { statType: 'DEX', currentLevel: 2, currentXp: 50,  xpForNextLevel: 150, status: 'Active' },
      { statType: 'CON', currentLevel: 4, currentXp: 230, xpForNextLevel: 250, status: 'Active' },
      { statType: 'WIL', currentLevel: 1, currentXp: 20,  xpForNextLevel: 100, status: 'Rusty'  },
    ],
  },
  bonusChallenge: null,
};

export const MOCK_ADMIN_CHARACTERS = [
  {
    id: CHAR_ID,
    characterName: 'IronHero',
    currentLevel: 5,
    overallXp: 250,
    xpForNextLevel: 500,
    streakCount: 3,
    lastWorkoutDate: '2026-06-21',
    createdAt: '2026-06-01T00:00:00',
    stats: [],
  },
  {
    id: '11112222-3333-4444-5555-666677778888',
    characterName: 'SteelWalker',
    currentLevel: 2,
    overallXp: 80,
    xpForNextLevel: 200,
    streakCount: 0,
    lastWorkoutDate: null,
    createdAt: '2026-06-10T00:00:00',
    stats: [],
  },
];

export { CHAR_ID };

/**
 * Intercept all calls to the default API origin (localhost:8080).
 */
export async function setupIndexApiMocks(page, {
  character = MOCK_CHARACTER,
  quests = MOCK_QUESTS,
  claimResult = MOCK_CLAIM_RESULT,
} = {}) {
  await page.route(`${API_ORIGIN}/**`, async (route) => {
    const url    = new URL(route.request().url());
    const path   = url.pathname;
    const method = route.request().method();

    if (path === '/api/quests' && method === 'GET') {
      return route.fulfill({ json: quests });
    }
    if (path === '/api/character' && method === 'POST') {
      return route.fulfill({ json: character, status: 201 });
    }
    if (path.startsWith('/api/character/') && !path.includes('/claim') && method === 'GET') {
      return route.fulfill({ json: character });
    }
    if (path.startsWith('/api/character/') && !path.includes('/claim') && method === 'DELETE') {
      return route.fulfill({ status: 204, body: '' });
    }
    if (path.endsWith('/claim') && method === 'POST') {
      return route.fulfill({ json: claimResult });
    }

    await route.abort();
  });
}

export async function setupAdminApiMocks(page, {
  quests = MOCK_QUESTS,
  characters = MOCK_ADMIN_CHARACTERS,
} = {}) {
  await page.route(`${API_ORIGIN}/**`, async (route) => {
    const url    = new URL(route.request().url());
    const path   = url.pathname;
    const method = route.request().method();

    if (path === '/api/admin/quests' && method === 'GET') {
      return route.fulfill({ json: quests });
    }
    if (path === '/api/admin/quests' && method === 'POST') {
      const body = JSON.parse(route.request().postData() || '{}');
      return route.fulfill({ json: body, status: 201 });
    }
    if (path.startsWith('/api/admin/quests/') && method === 'PUT') {
      const body = JSON.parse(route.request().postData() || '{}');
      return route.fulfill({ json: { ...body, questId: decodeURIComponent(path.split('/').pop()) } });
    }
    if (path.startsWith('/api/admin/quests/') && method === 'DELETE') {
      return route.fulfill({ status: 204, body: '' });
    }
    if (path === '/api/admin/characters' && method === 'GET') {
      return route.fulfill({ json: characters });
    }
    if (path.startsWith('/api/admin/characters/') && method === 'PUT') {
      const body = JSON.parse(route.request().postData() || '{}');
      const id   = decodeURIComponent(path.split('/').pop());
      const orig = characters.find(c => c.id === id) || characters[0];
      return route.fulfill({ json: { ...orig, ...body } });
    }
    if (path.startsWith('/api/admin/characters/') && method === 'DELETE') {
      return route.fulfill({ status: 204, body: '' });
    }

    await route.abort();
  });
}

/** Pre-load localStorage so the page boots with a known character. */
export async function seedCharacterStorage(page, charId = CHAR_ID) {
  await page.addInitScript((id) => {
    localStorage.setItem('ironpath_char_id', id);
  }, charId);
}

/** Pre-load sessionStorage with a valid admin Basic-Auth token. */
export async function seedAdminSession(page, user = 'admin', pass = 'ironpath_admin') {
  await page.addInitScript((token) => {
    sessionStorage.setItem('adminToken', token);
  }, btoa(`${user}:${pass}`));
}
