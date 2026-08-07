import { test, expect } from '@playwright/test';
import {
  CHAR_ID,
  MOCK_CHARACTER,
  MOCK_QUESTS,
  MOCK_CLAIM_RESULT,
  MOCK_LEVEL_UP_CLAIM_RESULT,
  MOCK_HISTORY,
  setupIndexApiMocks,
  seedCharacterStorage,
} from './helpers.js';

async function gotoApp(page) {
  await page.goto('/index.html');
}

async function clearStorage(page) {
  await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
}

// ---------------------------------------------------------------------------
// BOOT — no saved session
// ---------------------------------------------------------------------------

test.describe('Boot — no saved session', () => {
  test.beforeEach(async ({ page }) => {
    await clearStorage(page);
    await setupIndexApiMocks(page);
  });

  test('shows the Bind Your Soul (register) overlay', async ({ page }) => {
    await gotoApp(page);
    await expect(page.locator('#overlay')).toHaveClass(/show/);
    await expect(page.locator('#ovTitle')).toHaveText('Bind Your Soul');
  });

  test('register form shows username, password, security Q&A and operative name', async ({ page }) => {
    await gotoApp(page);
    await expect(page.locator('#ovUsernameField')).toBeVisible();
    await expect(page.locator('#ovPasswordField')).toBeVisible();
    await expect(page.locator('#ovQuestionField')).toBeVisible();
    await expect(page.locator('#ovAnswerField')).toBeVisible();
    await expect(page.locator('#ovNameField')).toBeVisible();
    await expect(page.locator('#ovSubmit')).toHaveText('Bind Soul');
  });

  test('app stays hidden until authenticated', async ({ page }) => {
    await gotoApp(page);
    await expect(page.locator('#app')).toHaveAttribute('hidden', '');
  });
});

// ---------------------------------------------------------------------------
// REGISTER — validation + success
// ---------------------------------------------------------------------------

test.describe('Register', () => {
  test.beforeEach(async ({ page }) => {
    await clearStorage(page);
    await setupIndexApiMocks(page);
    await gotoApp(page);
  });

  test('rejects a short username', async ({ page }) => {
    await page.locator('#ovUsername').fill('ab');
    await page.locator('#ovPassword').fill('correct-horse-battery');
    await page.locator('#ovSubmit').click();
    await expect(page.locator('#ovErr')).toContainText(/username/i);
  });

  test('rejects a short password', async ({ page }) => {
    await page.locator('#ovUsername').fill('ironhero');
    await page.locator('#ovPassword').fill('short');
    await page.locator('#ovSubmit').click();
    await expect(page.locator('#ovErr')).toContainText(/password/i);
  });

  test('requires an operative name', async ({ page }) => {
    await page.locator('#ovUsername').fill('ironhero');
    await page.locator('#ovPassword').fill('correct-horse-battery');
    await page.locator('#ovQuestion').fill('First pet?');
    await page.locator('#ovAnswer').fill('Sparky');
    await page.locator('#ovName').fill('');
    await page.locator('#ovSubmit').click();
    await expect(page.locator('#ovErr')).toContainText(/name/i);
  });

  test('requires a security question and answer', async ({ page }) => {
    await page.locator('#ovUsername').fill('ironhero');
    await page.locator('#ovPassword').fill('correct-horse-battery');
    await page.locator('#ovName').fill('IronHero');
    await page.locator('#ovQuestion').fill('');
    await page.locator('#ovSubmit').click();
    await expect(page.locator('#ovErr')).toContainText(/security question/i);
  });

  test('successful registration reveals the dashboard with the new character', async ({ page }) => {
    await page.locator('#ovUsername').fill('ironhero');
    await page.locator('#ovPassword').fill('correct-horse-battery');
    await page.locator('#ovQuestion').fill('First pet?');
    await page.locator('#ovAnswer').fill('Sparky');
    await page.locator('#ovName').fill('IronHero');
    await page.locator('#ovSubmit').click();

    await expect(page.locator('#app')).not.toHaveAttribute('hidden');
    await expect(page.locator('#overlay')).not.toHaveClass(/show/);
    await expect(page.locator('#charName')).toHaveText(MOCK_CHARACTER.characterName);
  });

  test('shows a "username taken" error on 409', async ({ page }) => {
    await page.route('**/api/auth/register', route =>
      route.fulfill({ status: 409, json: { message: 'Username taken' } })
    );
    await page.locator('#ovUsername').fill('ironhero');
    await page.locator('#ovPassword').fill('correct-horse-battery');
    await page.locator('#ovQuestion').fill('First pet?');
    await page.locator('#ovAnswer').fill('Sparky');
    await page.locator('#ovName').fill('IronHero');
    await page.locator('#ovSubmit').click();
    await expect(page.locator('#ovErr')).toContainText(/already taken/i);
  });

  test('the admin link is hidden for a non-admin account', async ({ page }) => {
    await page.locator('#ovUsername').fill('ironhero');
    await page.locator('#ovPassword').fill('correct-horse-battery');
    await page.locator('#ovQuestion').fill('First pet?');
    await page.locator('#ovAnswer').fill('Sparky');
    await page.locator('#ovName').fill('IronHero');
    await page.locator('#ovSubmit').click();
    await expect(page.locator('#app')).not.toHaveAttribute('hidden');
    await expect(page.locator('#adminLink')).toBeHidden();
  });
});

test.describe('Register as the first (admin) account', () => {
  test('shows the admin link once registered', async ({ page }) => {
    await clearStorage(page);
    await setupIndexApiMocks(page, { isAdmin: true });
    await gotoApp(page);
    await page.locator('#ovUsername').fill('firstadmin');
    await page.locator('#ovPassword').fill('correct-horse-battery');
    await page.locator('#ovQuestion').fill('First pet?');
    await page.locator('#ovAnswer').fill('Sparky');
    await page.locator('#ovName').fill('AdminHero');
    await page.locator('#ovSubmit').click();
    await expect(page.locator('#app')).not.toHaveAttribute('hidden');
    await expect(page.locator('#adminLink')).toBeVisible();
  });
});

// ---------------------------------------------------------------------------
// LOGIN
// ---------------------------------------------------------------------------

test.describe('Login', () => {
  test.beforeEach(async ({ page }) => {
    await clearStorage(page);
    await setupIndexApiMocks(page);
    await gotoApp(page);
    await page.locator('#ovToggleMode').click(); // register -> login
  });

  test('toggling to login swaps the form fields and copy', async ({ page }) => {
    await expect(page.locator('#ovTitle')).toHaveText('Reconnect');
    await expect(page.locator('#ovNameField')).toBeHidden();
    await expect(page.locator('#ovQuestionField')).toBeHidden();
    await expect(page.locator('#ovSubmit')).toHaveText('Log In');
  });

  test('shows an error on invalid credentials (401)', async ({ page }) => {
    await page.route('**/api/auth/login', route =>
      route.fulfill({ status: 401, json: { message: 'Invalid credentials' } })
    );
    await page.locator('#ovUsername').fill('ironhero');
    await page.locator('#ovPassword').fill('wrongpassword');
    await page.locator('#ovSubmit').click();
    await expect(page.locator('#ovErr')).toContainText(/invalid username or password/i);
  });

  test('successful login loads the existing character', async ({ page }) => {
    await page.locator('#ovUsername').fill('ironhero');
    await page.locator('#ovPassword').fill('correct-horse-battery');
    await page.locator('#ovSubmit').click();
    await expect(page.locator('#app')).not.toHaveAttribute('hidden');
    await expect(page.locator('#charName')).toHaveText(MOCK_CHARACTER.characterName);
  });

  test('login for an account with no character yet prompts to name one', async ({ page }) => {
    await page.route('**/api/character/mine', route =>
      route.fulfill({ status: 404, json: { message: 'Not found' } })
    );
    await page.locator('#ovUsername').fill('freshaccount');
    await page.locator('#ovPassword').fill('correct-horse-battery');
    await page.locator('#ovSubmit').click();
    await expect(page.locator('#ovTitle')).toHaveText('Name Your Operative');
    await expect(page.locator('#ovNameField')).toBeVisible();
  });
});

// ---------------------------------------------------------------------------
// FORGOT PASSWORD — 2-step recovery flow
// ---------------------------------------------------------------------------

test.describe('Forgot password', () => {
  test.beforeEach(async ({ page }) => {
    await clearStorage(page);
    await setupIndexApiMocks(page);
    await gotoApp(page);
    await page.locator('#ovToggleMode').click(); // -> login
  });

  test('login overlay has a forgot-password link', async ({ page }) => {
    await expect(page.locator('#ovForgotLinkWrap')).toBeVisible();
  });

  test('step 1 asks for the username and retrieves the security question', async ({ page }) => {
    await page.locator('#ovForgotLink').click();
    await expect(page.locator('#ovTitle')).toHaveText('Recover Access');
    await page.locator('#ovUsername').fill('ironhero');
    await page.locator('#ovSubmit').click();
    await expect(page.locator('#ovTitle')).toHaveText('Answer & Reset');
    await expect(page.locator('#ovForgotQuestionText')).toHaveText('First pet?');
  });

  test('step 2 requires an answer and a new password of at least 8 characters', async ({ page }) => {
    await page.locator('#ovForgotLink').click();
    await page.locator('#ovUsername').fill('ironhero');
    await page.locator('#ovSubmit').click();

    await page.locator('#ovAnswer').fill('');
    await page.locator('#ovSubmit').click();
    await expect(page.locator('#ovErr')).toContainText(/answer/i);

    await page.locator('#ovAnswer').fill('Sparky');
    await page.locator('#ovNewPassword').fill('short');
    await page.locator('#ovSubmit').click();
    await expect(page.locator('#ovErr')).toContainText(/8 characters/i);
  });

  test('a wrong answer shows an "incorrect answer" error', async ({ page }) => {
    await page.locator('#ovForgotLink').click();
    await page.locator('#ovUsername').fill('ironhero');
    await page.locator('#ovSubmit').click();
    await page.route('**/api/auth/reset-password', route =>
      route.fulfill({ status: 401, json: { message: 'Incorrect answer' } })
    );
    await page.locator('#ovAnswer').fill('WrongAnswer');
    await page.locator('#ovNewPassword').fill('new-correct-horse');
    await page.locator('#ovSubmit').click();
    await expect(page.locator('#ovErr')).toContainText(/incorrect answer/i);
  });

  test('successfully resetting the password signs the player in', async ({ page }) => {
    await page.locator('#ovForgotLink').click();
    await page.locator('#ovUsername').fill('ironhero');
    await page.locator('#ovSubmit').click();
    await page.locator('#ovAnswer').fill('Sparky');
    await page.locator('#ovNewPassword').fill('new-correct-horse');
    await page.locator('#ovSubmit').click();
    await expect(page.locator('#app')).not.toHaveAttribute('hidden');
  });
});

// ---------------------------------------------------------------------------
// BOOT — existing session in localStorage
// ---------------------------------------------------------------------------

test.describe('Boot — existing session', () => {
  test.beforeEach(async ({ page }) => {
    await clearStorage(page);
    await seedCharacterStorage(page);
    await setupIndexApiMocks(page);
  });

  test('shows the dashboard directly without the overlay', async ({ page }) => {
    await gotoApp(page);
    await expect(page.locator('#app')).not.toHaveAttribute('hidden');
    await expect(page.locator('#overlay')).not.toHaveClass(/show/);
  });

  test('renders character name, level and XP in the HUD', async ({ page }) => {
    await gotoApp(page);
    await expect(page.locator('#charName')).toHaveText(MOCK_CHARACTER.characterName);
    await expect(page.locator('#charLevel')).toHaveText(String(MOCK_CHARACTER.currentLevel));
    await expect(page.locator('#charXp')).toHaveText(String(MOCK_CHARACTER.overallXp));
    await expect(page.locator('#charXpMax')).toHaveText(String(MOCK_CHARACTER.xpForNextLevel));
  });

  test('the character XP meter reports its value via ARIA', async ({ page }) => {
    await gotoApp(page);
    const meter = page.locator('#charXpMeter');
    await expect(meter).toHaveAttribute('aria-valuenow', String(MOCK_CHARACTER.overallXp));
    await expect(meter).toHaveAttribute('aria-valuemax', String(MOCK_CHARACTER.xpForNextLevel));
  });

  test('renders streak count and XP buff multiplier', async ({ page }) => {
    await gotoApp(page);
    await expect(page.locator('#streakDay')).toHaveText(String(MOCK_CHARACTER.streakCount));
    await expect(page.locator('#buffVal')).toHaveText('1.15'); // 1 + 3*0.05
  });

  test('connection indicator shows linked', async ({ page }) => {
    await gotoApp(page);
    await expect(page.locator('#connDot')).toHaveClass(/ok/);
    await expect(page.locator('#connText')).toContainText(/linked/i);
  });

  test('a 404 on the character fetch clears the session and reopens registration', async ({ page }) => {
    await page.route(`**/api/character/${CHAR_ID}`, route =>
      route.fulfill({ status: 404, json: { message: 'Not found' } })
    );
    await gotoApp(page);
    await expect(page.locator('#overlay')).toHaveClass(/show/);
    await expect(page.locator('#ovTitle')).toHaveText('Bind Your Soul');
  });

  test('a 401 on the character fetch clears the session and shows the login form', async ({ page }) => {
    await page.route(`**/api/character/${CHAR_ID}`, route =>
      route.fulfill({ status: 401, json: { message: 'Unauthorized' } })
    );
    await gotoApp(page);
    await expect(page.locator('#overlay')).toHaveClass(/show/);
    await expect(page.locator('#ovTitle')).toHaveText('Reconnect');
  });

  test('shows an error banner and a red connection dot when the API is unreachable', async ({ page }) => {
    await page.route('**/api/**', route => route.abort('failed'));
    await gotoApp(page);
    await expect(page.locator('#banner')).toHaveClass(/show/);
    await expect(page.locator('#connDot')).toHaveClass(/bad/);
  });
});

// ---------------------------------------------------------------------------
// ATTRIBUTES PANEL
// ---------------------------------------------------------------------------

test.describe('Attributes panel', () => {
  test.beforeEach(async ({ page }) => {
    await clearStorage(page);
    await seedCharacterStorage(page);
    await setupIndexApiMocks(page);
    await gotoApp(page);
  });

  test('renders all four stat blocks in STR/DEX/CON/WIL order', async ({ page }) => {
    const stats = page.locator('#statList .stat');
    await expect(stats).toHaveCount(4);
    await expect(stats.nth(0)).toContainText('STR');
    await expect(stats.nth(1)).toContainText('DEX');
    await expect(stats.nth(2)).toContainText('CON');
    await expect(stats.nth(3)).toContainText('WIL');
  });

  test('an Active stat shows the Active badge', async ({ page }) => {
    const str = page.locator('#statList .stat').filter({ hasText: 'Strength' });
    await expect(str.locator('.stat__status')).toHaveText('Active');
    await expect(str.locator('.stat__status')).toHaveClass(/is-active/);
  });

  test('a decayed stat shows the Rusty badge', async ({ page }) => {
    const wil = page.locator('#statList .stat').filter({ hasText: 'Willpower' });
    await expect(wil.locator('.stat__status')).toHaveText('Rusty');
    await expect(wil.locator('.stat__status')).toHaveClass(/is-rusty/);
  });

  test('each stat gauge exposes its XP via ARIA', async ({ page }) => {
    const con = page.locator('#statList .stat').filter({ hasText: 'Constitution' });
    const bar = con.locator('[role="meter"]');
    await expect(bar).toHaveAttribute('aria-valuenow', '180');
    await expect(bar).toHaveAttribute('aria-valuemax', '250');
  });

  test('stat XP bar fill has non-zero width', async ({ page }) => {
    const bar = page.locator('#statList .stat').filter({ hasText: 'Strength' }).locator('.bar__fill');
    const width = await bar.evaluate(el => el.style.width);
    expect(parseFloat(width)).toBeGreaterThan(0);
  });
});

// ---------------------------------------------------------------------------
// QUEST BOARD
// ---------------------------------------------------------------------------

test.describe('Quest board', () => {
  test.beforeEach(async ({ page }) => {
    await clearStorage(page);
    await seedCharacterStorage(page);
    await setupIndexApiMocks(page);
    await gotoApp(page);
  });

  test('renders a card for every available quest', async ({ page }) => {
    await expect(page.locator('#questList .qcard')).toHaveCount(MOCK_QUESTS.length);
  });

  test('the Daily Focus quest is visually distinguished from the rest', async ({ page }) => {
    const cards = page.locator('#questList .qcard');
    const dailyCard = cards.filter({ hasText: 'Weight Training Session' });
    const regularCard = cards.filter({ hasText: 'Morning 5k Run' });
    await expect(dailyCard).toHaveClass(/is-daily/);
    await expect(dailyCard.locator('.daily-badge')).toContainText('Daily Focus');
    await expect(regularCard).not.toHaveClass(/is-daily/);
    await expect(regularCard.locator('.daily-badge')).toHaveCount(0);
  });

  test('quest body is collapsed until its header is clicked', async ({ page }) => {
    const card = page.locator('#questList .qcard').first();
    await expect(card.locator('.qcard__body')).toBeHidden();
    await card.locator('.qcard__header').click();
    await expect(card).toHaveClass(/is-active/);
    await expect(card.locator('.qcard__body')).toBeVisible();
    await card.locator('.qcard__header').click();
    await expect(card).not.toHaveClass(/is-active/);
  });

  test('expanded body shows the description and reward preview', async ({ page }) => {
    const card = page.locator('#questList .qcard').filter({ hasText: 'Morning 5k Run' });
    await card.locator('.qcard__header').click();
    await expect(card.locator('.qcard__desc')).toContainText('5 km run');
    await expect(card.locator('.rewards')).toContainText('CON XP');
  });

  test('the Complete button is disabled until the objective checkbox is checked', async ({ page }) => {
    const card = page.locator('#questList .qcard').first();
    await card.locator('.qcard__header').click();
    await expect(card.locator('button.complete')).toBeDisabled();
    await card.locator('label.obj').click();
    await expect(card.locator('button.complete')).toBeEnabled();
  });

  test('tag filter chips toggle active state and refetch the catalog', async ({ page }) => {
    let lastTagRequested = null;
    await page.route('**/api/quests**', async (route) => {
      const url = new URL(route.request().url());
      lastTagRequested = url.searchParams.get('tag');
      return route.fulfill({ json: MOCK_QUESTS });
    });
    const chip = page.locator('.tag-chip[data-tag="CARDIO"]');
    await chip.click();
    await expect(chip).toHaveClass(/active/);
    expect(lastTagRequested).toBe('CARDIO');
  });
});

// ---------------------------------------------------------------------------
// CLAIM QUEST
// ---------------------------------------------------------------------------

test.describe('Claim quest', () => {
  test.beforeEach(async ({ page }) => {
    await clearStorage(page);
    await seedCharacterStorage(page);
    await setupIndexApiMocks(page);
    await gotoApp(page);
  });

  async function expandAndClaim(page, index = 0) {
    const card = page.locator('#questList .qcard').nth(index);
    await card.locator('.qcard__header').click();
    await card.locator('label.obj').click();
    await expect(card.locator('button.complete')).toBeEnabled();
    await card.locator('button.complete').click();
  }

  test('updates the HUD XP and streak after claiming', async ({ page }) => {
    await expandAndClaim(page);
    await expect(page.locator('#charXp')).toHaveText(String(MOCK_CLAIM_RESULT.character.overallXp));
    await expect(page.locator('#streakDay')).toHaveText(String(MOCK_CLAIM_RESULT.character.streakCount));
  });

  test('marks the claimed quest as cleared with a disabled button', async ({ page }) => {
    await expandAndClaim(page);
    const card = page.locator('#questList .qcard').first();
    await expect(card).toHaveClass(/is-cleared/);
    await card.locator('.qcard__header').click();
    await expect(card.locator('button.complete')).toBeDisabled();
    await expect(card.locator('button.complete')).toContainText(/cleared/i);
  });

  test('adds a "Quest cleared" line to the combat log', async ({ page }) => {
    await expandAndClaim(page);
    await expect(page.locator('#logBody')).toContainText('Quest cleared');
  });

  test('a level-up flashes the HUD panel and pulses the level badge', async ({ page }) => {
    await page.route('**/claim', route =>
      route.fulfill({ json: MOCK_LEVEL_UP_CLAIM_RESULT })
    );
    await expandAndClaim(page);
    await expect(page.locator('#hudPanel')).toHaveClass(/flash-levelup/);
    await expect(page.locator('#charLevel')).toHaveText(String(MOCK_LEVEL_UP_CLAIM_RESULT.character.currentLevel));
    await expect(page.locator('#logBody')).toContainText('ascends');
  });

  test('a bonus-challenge roll flashes the HUD panel and logs the bonus', async ({ page }) => {
    await page.route('**/claim', route =>
      route.fulfill({ json: { ...MOCK_CLAIM_RESULT, bonusChallenge: { description: 'Extra set', bonusXp: 20 } } })
    );
    await expandAndClaim(page);
    await expect(page.locator('#hudPanel')).toHaveClass(/flash-bonus/);
    await expect(page.locator('#logBody')).toContainText('Bonus Challenge');
  });
});

// ---------------------------------------------------------------------------
// PROGRESS DASHBOARD
// ---------------------------------------------------------------------------

test.describe('Progress dashboard', () => {
  test('renders 14 daily XP bars and a stat-distribution legend when history exists', async ({ page }) => {
    await clearStorage(page);
    await seedCharacterStorage(page);
    await setupIndexApiMocks(page, { history: MOCK_HISTORY });
    await gotoApp(page);
    await expect(page.locator('#progressEmpty')).toBeHidden();
    await expect(page.locator('#xpChart .xp-bar')).toHaveCount(14);
    await expect(page.locator('#statDistLegend')).toContainText('Constitution');
  });

  test('shows an empty state when there is no claim history', async ({ page }) => {
    await clearStorage(page);
    await seedCharacterStorage(page);
    await setupIndexApiMocks(page, { history: [] });
    await gotoApp(page);
    await expect(page.locator('#progressEmpty')).toBeVisible();
    await expect(page.locator('#progressEmpty')).toContainText(/no quests claimed/i);
  });
});

// ---------------------------------------------------------------------------
// COMBAT LOG
// ---------------------------------------------------------------------------

test.describe('Combat log', () => {
  test('shows a system re-link message on boot with an existing session', async ({ page }) => {
    await clearStorage(page);
    await seedCharacterStorage(page);
    await setupIndexApiMocks(page);
    await gotoApp(page);
    await expect(page.locator('#logBody')).toContainText('Soul-link re-established');
  });
});

// ---------------------------------------------------------------------------
// ACCOUNT SECURITY PANEL
// ---------------------------------------------------------------------------

test.describe('Account security panel', () => {
  test.beforeEach(async ({ page }) => {
    await clearStorage(page);
    await seedCharacterStorage(page);
  });

  test('shows a prompt to set a question when none exists yet', async ({ page }) => {
    await setupIndexApiMocks(page, { securityQuestion: null });
    await gotoApp(page);
    await page.locator('#openAccountBtn').click();
    await expect(page.locator('#accountOverlay')).toHaveClass(/show/);
    await expect(page.locator('#acctSub')).toContainText(/no recovery question set/i);
  });

  test('shows the current question when one is already set', async ({ page }) => {
    await setupIndexApiMocks(page, { securityQuestion: 'First pet?' });
    await gotoApp(page);
    await page.locator('#openAccountBtn').click();
    await expect(page.locator('#acctSub')).toContainText('First pet?');
  });

  test('requires the current password, a question and an answer to save', async ({ page }) => {
    await setupIndexApiMocks(page);
    await gotoApp(page);
    await page.locator('#openAccountBtn').click();
    await page.locator('#acctSubmit').click();
    await expect(page.locator('#acctErr')).toContainText(/current password/i);
  });

  test('saving updates the question and logs a system message', async ({ page }) => {
    await setupIndexApiMocks(page);
    await gotoApp(page);
    await page.locator('#openAccountBtn').click();
    await page.locator('#acctCurrentPassword').fill('correct-horse-battery');
    await page.locator('#acctQuestion').fill('Favorite color?');
    await page.locator('#acctAnswer').fill('Blue');
    await page.locator('#acctSubmit').click();
    await expect(page.locator('#accountOverlay')).not.toHaveClass(/show/);
    await expect(page.locator('#logBody')).toContainText('Recovery question updated');
  });

  test('close button hides the overlay', async ({ page }) => {
    await setupIndexApiMocks(page);
    await gotoApp(page);
    await page.locator('#openAccountBtn').click();
    await page.locator('#accountCloseBtn').click();
    await expect(page.locator('#accountOverlay')).not.toHaveClass(/show/);
  });
});

// ---------------------------------------------------------------------------
// ALLIES OVERLAY
// ---------------------------------------------------------------------------

test.describe('Allies overlay', () => {
  test.beforeEach(async ({ page }) => {
    await clearStorage(page);
    await seedCharacterStorage(page);
    await setupIndexApiMocks(page);
    await gotoApp(page);
    await page.locator('#openFriendsBtn').click();
    await expect(page.locator('#friendsOverlay')).toHaveClass(/show/);
  });

  test('defaults to the Friends tab with an empty state', async ({ page }) => {
    await expect(page.locator('.ftab[data-tab="list"]')).toHaveClass(/active/);
    await expect(page.locator('#friendsListBody')).toContainText(/no allies yet/i);
  });

  test('switches to the Guild Hall activity feed tab', async ({ page }) => {
    await page.locator('.ftab[data-tab="feed"]').click();
    await expect(page.locator('#ftabFeed')).toBeVisible();
    await expect(page.locator('#friendFeedBody')).toContainText(/no recent activity/i);
  });

  test('switches to the Requests tab', async ({ page }) => {
    await page.locator('.ftab[data-tab="requests"]').click();
    await expect(page.locator('#ftabRequests')).toBeVisible();
    await expect(page.locator('#incomingListBody')).toContainText(/no incoming requests/i);
  });

  test('switches to the Privacy tab and shows the default visibility selector', async ({ page }) => {
    await page.locator('.ftab[data-tab="settings"]').click();
    await expect(page.locator('#ftabSettings')).toBeVisible();
    await expect(page.locator('#defaultVisibilitySelect')).toHaveValue('BASIC');
  });

  test('sending a friend request for an unknown username shows an error', async ({ page }) => {
    await page.locator('#addFriendInput').fill('nobody');
    await page.locator('#addFriendBtn').click();
    await expect(page.locator('#friendsErr')).toContainText(/no operative found/i);
  });

  test('close button hides the overlay', async ({ page }) => {
    await page.locator('#friendsCloseBtn').click();
    await expect(page.locator('#friendsOverlay')).not.toHaveClass(/show/);
  });
});

// ---------------------------------------------------------------------------
// ABANDON RUN
// ---------------------------------------------------------------------------

test.describe('Abandon run', () => {
  test.beforeEach(async ({ page }) => {
    await clearStorage(page);
    await seedCharacterStorage(page);
    await setupIndexApiMocks(page);
    await gotoApp(page);
  });

  test('asks for confirmation before deleting the character', async ({ page }) => {
    let confirmed = false;
    page.once('dialog', async dialog => { confirmed = true; await dialog.dismiss(); });
    await page.locator('#abandonBtn').click();
    expect(confirmed).toBe(true);
  });

  test('cancelling the dialog keeps the dashboard visible', async ({ page }) => {
    page.once('dialog', dialog => dialog.dismiss());
    await page.locator('#abandonBtn').click();
    await expect(page.locator('#app')).not.toHaveAttribute('hidden');
  });

  test('confirming resets to the registration overlay', async ({ page }) => {
    page.once('dialog', dialog => dialog.accept());
    await page.locator('#abandonBtn').click();
    await expect(page.locator('#overlay')).toHaveClass(/show/);
    await expect(page.locator('#ovTitle')).toHaveText('Bind Your Soul');
  });
});

// ---------------------------------------------------------------------------
// NAVIGATION
// ---------------------------------------------------------------------------

test.describe('Navigation', () => {
  test('the Admin link is hidden for a non-admin session', async ({ page }) => {
    await clearStorage(page);
    await seedCharacterStorage(page, { isAdmin: false });
    await setupIndexApiMocks(page);
    await gotoApp(page);
    await expect(page.locator('#adminLink')).toBeHidden();
  });

  test('the Admin link is visible and points to admin.html for an admin session', async ({ page }) => {
    await clearStorage(page);
    await seedCharacterStorage(page, { isAdmin: true });
    await setupIndexApiMocks(page, { isAdmin: true });
    await gotoApp(page);
    const link = page.locator('#adminLink');
    await expect(link).toBeVisible();
    await expect(link).toHaveAttribute('href', 'admin.html');
  });

  test('logout clears the session and shows the login overlay', async ({ page }) => {
    await clearStorage(page);
    await seedCharacterStorage(page);
    await setupIndexApiMocks(page);
    await gotoApp(page);
    page.once('dialog', dialog => dialog.accept());
    await page.locator('#logoutBtn').click();
    await expect(page.locator('#overlay')).toHaveClass(/show/);
    await expect(page.locator('#ovTitle')).toHaveText('Reconnect');
    const token = await page.evaluate(() => localStorage.getItem('ironpath_token'));
    expect(token).toBeNull();
  });
});
