import { test, expect } from '@playwright/test';
import {
  CHAR_ID,
  MOCK_CHARACTER,
  MOCK_QUESTS,
  MOCK_CLAIM_RESULT,
  setupIndexApiMocks,
  seedCharacterStorage,
} from './helpers.js';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function gotoApp(page) {
  await page.goto('/index.html');
}

// ---------------------------------------------------------------------------
// BOOT — no saved character
// ---------------------------------------------------------------------------

test.describe('Boot — no saved character', () => {
  test.beforeEach(async ({ page }) => {
    // clear any storage left from a previous test
    await page.addInitScript(() => {
      localStorage.clear();
      sessionStorage.clear();
    });
    await setupIndexApiMocks(page);
  });

  test('shows the Bind Your Soul overlay', async ({ page }) => {
    await gotoApp(page);
    await expect(page.locator('#overlay')).toHaveClass(/show/);
    await expect(page.locator('#ovTitle')).toHaveText('Bind Your Soul');
  });

  test('overlay shows operative name field and hides cancel button', async ({ page }) => {
    await gotoApp(page);
    await expect(page.locator('#ovNameField')).toBeVisible();
    await expect(page.locator('#ovCancel')).toBeHidden();
    await expect(page.locator('#ovSubmit')).toHaveText('Bind Soul');
  });

  test('pre-fills the API endpoint with the default value', async ({ page }) => {
    await gotoApp(page);
    await expect(page.locator('#ovApi')).toHaveValue('http://localhost:8080');
  });
});

// ---------------------------------------------------------------------------
// BIND SOUL — form validation
// ---------------------------------------------------------------------------

test.describe('Bind Soul — form validation', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await setupIndexApiMocks(page);
    await gotoApp(page);
  });

  test('shows an error when the API endpoint is empty', async ({ page }) => {
    await page.locator('#ovApi').fill('');
    await page.locator('#ovSubmit').click();
    await expect(page.locator('#ovErr')).toHaveText(/required/i);
  });

  test('shows an error when the operative name is empty', async ({ page }) => {
    await page.locator('#ovApi').fill('http://localhost:8080');
    await page.locator('#ovName').fill('');
    await page.locator('#ovSubmit').click();
    await expect(page.locator('#ovErr')).toHaveText(/name/i);
  });
});

// ---------------------------------------------------------------------------
// BIND SOUL — successful character creation
// ---------------------------------------------------------------------------

test.describe('Bind Soul — create character', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await setupIndexApiMocks(page);
  });

  test('creates a character and reveals the main app', async ({ page }) => {
    await gotoApp(page);

    await page.locator('#ovApi').fill('http://localhost:8080');
    await page.locator('#ovName').fill('IronHero');
    await page.locator('#ovSubmit').click();

    await expect(page.locator('#app')).not.toHaveAttribute('hidden');
    await expect(page.locator('#overlay')).not.toHaveClass(/show/);
    await expect(page.locator('#charName')).toHaveText('IronHero');
  });

  test('logs a system message after binding', async ({ page }) => {
    await gotoApp(page);
    await page.locator('#ovApi').fill('http://localhost:8080');
    await page.locator('#ovName').fill('IronHero');
    await page.locator('#ovSubmit').click();

    await expect(page.locator('#logBody')).toContainText('Soul bound');
  });

  test('connection dot turns green after binding', async ({ page }) => {
    await gotoApp(page);
    await page.locator('#ovApi').fill('http://localhost:8080');
    await page.locator('#ovName').fill('IronHero');
    await page.locator('#ovSubmit').click();

    await expect(page.locator('#connDot')).toHaveClass(/ok/);
  });
});

// ---------------------------------------------------------------------------
// BOOT — existing character in localStorage
// ---------------------------------------------------------------------------

test.describe('Boot — existing character', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await seedCharacterStorage(page);
    await setupIndexApiMocks(page);
  });

  test('shows the main app without the overlay', async ({ page }) => {
    await gotoApp(page);
    await expect(page.locator('#app')).not.toHaveAttribute('hidden');
    await expect(page.locator('#overlay')).not.toHaveClass(/show/);
  });

  test('renders character name and level in the HUD', async ({ page }) => {
    await gotoApp(page);
    await expect(page.locator('#charName')).toHaveText(MOCK_CHARACTER.characterName);
    await expect(page.locator('#charLevel')).toHaveText(String(MOCK_CHARACTER.currentLevel));
  });

  test('renders overall XP values in the HUD', async ({ page }) => {
    await gotoApp(page);
    await expect(page.locator('#charXp')).toHaveText(String(MOCK_CHARACTER.overallXp));
    await expect(page.locator('#charXpMax')).toHaveText(String(MOCK_CHARACTER.xpForNextLevel));
  });

  test('renders streak count and XP buff multiplier', async ({ page }) => {
    await gotoApp(page);
    await expect(page.locator('#streakDay')).toHaveText(String(MOCK_CHARACTER.streakCount));
    // streak 3 → 1 + 3*0.05 = 1.15
    await expect(page.locator('#buffVal')).toHaveText('1.15');
  });

  test('renders the XP progress bar', async ({ page }) => {
    await gotoApp(page);
    const fill = page.locator('#charXpFill');
    const width = await fill.evaluate(el => el.style.width);
    expect(parseFloat(width)).toBeGreaterThan(0);
    expect(parseFloat(width)).toBeLessThanOrEqual(100);
  });

  test('connection indicator shows linked', async ({ page }) => {
    await gotoApp(page);
    await expect(page.locator('#connDot')).toHaveClass(/ok/);
    await expect(page.locator('#connText')).toContainText('linked');
  });
});

// ---------------------------------------------------------------------------
// ATTRIBUTES PANEL
// ---------------------------------------------------------------------------

test.describe('Attributes panel', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await seedCharacterStorage(page);
    await setupIndexApiMocks(page);
    await gotoApp(page);
  });

  test('renders all four stat blocks', async ({ page }) => {
    const stats = page.locator('#statList .stat');
    await expect(stats).toHaveCount(4);
  });

  test('renders the STR stat with Active status', async ({ page }) => {
    const str = page.locator('#statList .stat').filter({ hasText: 'STR' });
    await expect(str).toBeVisible();
    await expect(str.locator('.stat__status')).toHaveText('Active');
    await expect(str.locator('.stat__status')).toHaveClass(/is-active/);
  });

  test('renders the WIL stat with Rusty status', async ({ page }) => {
    const wil = page.locator('#statList .stat').filter({ hasText: 'WIL' });
    await expect(wil.locator('.stat__status')).toHaveText('Rusty');
    await expect(wil.locator('.stat__status')).toHaveClass(/is-rusty/);
  });

  test('each stat shows current level', async ({ page }) => {
    const con = page.locator('#statList .stat').filter({ hasText: 'CON' });
    await expect(con).toContainText('LV 4');
  });

  test('each stat shows XP numbers', async ({ page }) => {
    const str = page.locator('#statList .stat').filter({ hasText: 'STR' });
    await expect(str.locator('.stat__xp')).toContainText('100 / 200 XP');
  });

  test('stat XP bar has non-zero width', async ({ page }) => {
    const bar = page.locator('#statList .stat').filter({ hasText: 'STR' }).locator('.bar__fill');
    const width = await bar.evaluate(el => el.style.width);
    expect(parseFloat(width)).toBeGreaterThan(0);
  });
});

// ---------------------------------------------------------------------------
// QUEST BOARD
// ---------------------------------------------------------------------------

test.describe('Quest Board', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await seedCharacterStorage(page);
    await setupIndexApiMocks(page);
    await gotoApp(page);
  });

  test('renders quest cards for each available quest', async ({ page }) => {
    const cards = page.locator('#questList .qcard');
    await expect(cards).toHaveCount(MOCK_QUESTS.length);
  });

  test('quest cards show the quest title', async ({ page }) => {
    await expect(page.locator('#questList')).toContainText('Morning 5k Run');
    await expect(page.locator('#questList')).toContainText('Weight Training Session');
  });

  test('quest body is hidden before expanding', async ({ page }) => {
    const body = page.locator('#questList .qcard').first().locator('.qcard__body');
    await expect(body).toBeHidden();
  });

  test('clicking a quest header expands it', async ({ page }) => {
    await page.locator('#questList .qcard').first().locator('.qcard__header').click();
    const card = page.locator('#questList .qcard').first();
    await expect(card).toHaveClass(/is-active/);
    await expect(card.locator('.qcard__body')).toBeVisible();
  });

  test('clicking an expanded quest header collapses it', async ({ page }) => {
    const header = page.locator('#questList .qcard').first().locator('.qcard__header');
    await header.click();
    await header.click();
    const card = page.locator('#questList .qcard').first();
    await expect(card).not.toHaveClass(/is-active/);
    await expect(card.locator('.qcard__body')).toBeHidden();
  });

  test('quest body shows description text', async ({ page }) => {
    await page.locator('#questList .qcard').first().locator('.qcard__header').click();
    const body = page.locator('#questList .qcard').first().locator('.qcard__body');
    await expect(body.locator('.qcard__desc')).toContainText('5 km run');
  });

  test('quest body shows reward preview', async ({ page }) => {
    await page.locator('#questList .qcard').first().locator('.qcard__header').click();
    const body = page.locator('#questList .qcard').first().locator('.qcard__body');
    await expect(body.locator('.rewards')).toBeVisible();
    await expect(body.locator('.rewards')).toContainText('CON XP');
  });

  test('Complete button is disabled until checkbox is checked', async ({ page }) => {
    await page.locator('#questList .qcard').first().locator('.qcard__header').click();
    const btn = page.locator('#questList .qcard').first().locator('button.complete');
    await expect(btn).toBeDisabled();
  });

  test('checking the objective checkbox enables the Complete button', async ({ page }) => {
    await page.locator('#questList .qcard').first().locator('.qcard__header').click();
    const card = page.locator('#questList .qcard').first();
    // The checkbox input is hidden (opacity:0 width:0 height:0); click the label instead
    await card.locator('label.obj').click();
    await expect(page.locator('#questList .qcard').first().locator('button.complete')).toBeEnabled();
  });

  test('shows estimated XP in the quest header', async ({ page }) => {
    const meta = page.locator('#questList .qcard').first().locator('.qcard__meta');
    await expect(meta).toContainText('XP');
  });
});

// ---------------------------------------------------------------------------
// CLAIM QUEST
// ---------------------------------------------------------------------------

test.describe('Claim quest', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await seedCharacterStorage(page);
    await setupIndexApiMocks(page);
    await gotoApp(page);
  });

  async function expandAndClaim(page, index = 0) {
    // Expand the card
    await page.locator('#questList .qcard').nth(index).locator('.qcard__header').click();
    // Click the label (the hidden input is zero-size; clicking the visible label is reliable)
    await page.locator('#questList .qcard').nth(index).locator('label.obj').click();
    // Wait for the Complete button to become enabled after the re-render
    await expect(page.locator('#questList .qcard').nth(index).locator('button.complete')).toBeEnabled();
    await page.locator('#questList .qcard').nth(index).locator('button.complete').click();
  }

  test('updates the character XP in the HUD after claiming', async ({ page }) => {
    await expandAndClaim(page);
    await expect(page.locator('#charXp')).toHaveText(String(MOCK_CLAIM_RESULT.character.overallXp));
  });

  test('updates the streak count in the HUD after claiming', async ({ page }) => {
    await expandAndClaim(page);
    await expect(page.locator('#streakDay')).toHaveText(String(MOCK_CLAIM_RESULT.character.streakCount));
  });

  test('marks the claimed quest as cleared', async ({ page }) => {
    await expandAndClaim(page);
    const card = page.locator('#questList .qcard').first();
    await expect(card).toHaveClass(/is-cleared/);
  });

  test('claimed quest shows disabled "Quest Cleared ✓" button', async ({ page }) => {
    await expandAndClaim(page);
    await page.locator('#questList .qcard').first().locator('.qcard__header').click();
    const btn = page.locator('#questList .qcard').first().locator('button.complete');
    await expect(btn).toBeDisabled();
    await expect(btn).toContainText('Quest Cleared');
  });

  test('adds quest result lines to the combat log', async ({ page }) => {
    await expandAndClaim(page);
    await expect(page.locator('#logBody')).toContainText('Quest cleared');
  });

  test('second claim on same quest is not possible (no checkbox shown)', async ({ page }) => {
    await expandAndClaim(page);
    await page.locator('#questList .qcard').first().locator('.qcard__header').click();
    await expect(page.locator('#questList .qcard').first().locator('.qc-check')).toHaveCount(0);
  });
});

// ---------------------------------------------------------------------------
// API SETTINGS OVERLAY
// ---------------------------------------------------------------------------

test.describe('API settings overlay', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await seedCharacterStorage(page);
    await setupIndexApiMocks(page);
    await gotoApp(page);
  });

  test('gear button opens the API settings overlay', async ({ page }) => {
    await page.locator('#gearBtn').click();
    await expect(page.locator('#overlay')).toHaveClass(/show/);
    await expect(page.locator('#ovTitle')).toHaveText('API Settings');
  });

  test('settings overlay hides the name field and shows cancel', async ({ page }) => {
    await page.locator('#gearBtn').click();
    await expect(page.locator('#ovNameField')).toBeHidden();
    await expect(page.locator('#ovCancel')).toBeVisible();
    await expect(page.locator('#ovSubmit')).toHaveText('Save & Reconnect');
  });

  test('cancel button closes the settings overlay', async ({ page }) => {
    await page.locator('#gearBtn').click();
    await page.locator('#ovCancel').click();
    await expect(page.locator('#overlay')).not.toHaveClass(/show/);
  });

  test('Escape key closes the settings overlay', async ({ page }) => {
    await page.locator('#gearBtn').click();
    await page.keyboard.press('Escape');
    await expect(page.locator('#overlay')).not.toHaveClass(/show/);
  });

  test('saving a new API endpoint reconnects and updates the connection label', async ({ page }) => {
    await page.locator('#gearBtn').click();
    await page.locator('#ovApi').fill('http://localhost:8080');
    await page.locator('#ovSubmit').click();
    await expect(page.locator('#overlay')).not.toHaveClass(/show/);
    await expect(page.locator('#connText')).toContainText('linked');
  });
});

// ---------------------------------------------------------------------------
// COMBAT LOG
// ---------------------------------------------------------------------------

test.describe('Combat log', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await seedCharacterStorage(page);
    await setupIndexApiMocks(page);
    await gotoApp(page);
  });

  test('is visible on boot', async ({ page }) => {
    await expect(page.locator('#logBody')).toBeVisible();
  });

  test('shows a system re-link message on boot with existing character', async ({ page }) => {
    // seed a pre-existing log entry
    await page.addInitScript((id) => {
      localStorage.setItem(`ironpath_log_${id}`, JSON.stringify([
        { category: 'system', html: 'SYSTEM: Soul-link re-established with <span class="hl">IronHero</span>.' },
      ]));
    }, CHAR_ID);
    await page.reload();
    await expect(page.locator('#logBody')).toContainText('Soul-link re-established');
  });
});

// ---------------------------------------------------------------------------
// ERROR HANDLING
// ---------------------------------------------------------------------------

test.describe('Error handling', () => {
  test('shows an error banner when API is unreachable on boot', async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await seedCharacterStorage(page);
    // Route all API calls to network failure
    await page.route('http://localhost:8080/**', route => route.abort('failed'));
    await gotoApp(page);
    await expect(page.locator('#banner')).toHaveClass(/show/);
    await expect(page.locator('#banner')).toContainText(/Connection severed|connect/i);
  });

  test('connection dot turns red when API is unreachable', async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await seedCharacterStorage(page);
    await page.route('http://localhost:8080/**', route => route.abort('failed'));
    await gotoApp(page);
    await expect(page.locator('#connDot')).toHaveClass(/bad/);
  });

  test('shows error in bind overlay when API is unreachable during creation', async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await page.route('http://localhost:8080/**', route => route.abort('failed'));
    await gotoApp(page);
    await page.locator('#ovApi').fill('http://localhost:8080');
    await page.locator('#ovName').fill('Hero');
    await page.locator('#ovSubmit').click();
    await expect(page.locator('#ovErr')).toContainText(/Cannot reach|server/i);
  });

  test('shows 404 error message when character no longer exists', async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await seedCharacterStorage(page);
    await page.route('http://localhost:8080/**', route =>
      route.fulfill({ status: 404, json: { message: 'Character not found' } })
    );
    await gotoApp(page);
    // 404 on boot clears the char ID and re-shows the bind overlay
    await expect(page.locator('#overlay')).toHaveClass(/show/);
    await expect(page.locator('#ovTitle')).toHaveText('Bind Your Soul');
  });
});

// ---------------------------------------------------------------------------
// ABANDON RUN
// ---------------------------------------------------------------------------

test.describe('Abandon run', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await seedCharacterStorage(page);
    await setupIndexApiMocks(page);
    await gotoApp(page);
  });

  test('abandon button is visible', async ({ page }) => {
    await expect(page.locator('#abandonBtn')).toBeVisible();
  });

  test('confirms before abandoning', async ({ page }) => {
    let confirmed = false;
    page.once('dialog', async dialog => {
      confirmed = true;
      await dialog.dismiss();
    });
    await page.locator('#abandonBtn').click();
    expect(confirmed).toBe(true);
  });

  test('cancelling the dialog keeps the main app visible', async ({ page }) => {
    page.once('dialog', dialog => dialog.dismiss());
    await page.locator('#abandonBtn').click();
    await expect(page.locator('#app')).not.toHaveAttribute('hidden');
  });

  test('confirming abandon resets to the Bind Your Soul overlay', async ({ page }) => {
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
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await seedCharacterStorage(page);
    await setupIndexApiMocks(page);
    await gotoApp(page);
  });

  test('admin link is present and points to admin.html', async ({ page }) => {
    const link = page.locator('a.admin-link');
    await expect(link).toBeVisible();
    await expect(link).toHaveAttribute('href', 'admin.html');
  });
});
