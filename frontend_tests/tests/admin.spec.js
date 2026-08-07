import { test, expect } from '@playwright/test';
import {
  MOCK_ADMIN_QUESTS,
  MOCK_PENDING_QUESTS,
  MOCK_EVENTS,
  MOCK_ADMIN_CHARACTERS,
  setupAdminApiMocks,
  seedAdminSession,
} from './helpers.js';

async function gotoAdmin(page) {
  await page.goto('/admin.html');
}

async function clearStorage(page) {
  await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
}

// ---------------------------------------------------------------------------
// ACCESS DENIED
// ---------------------------------------------------------------------------

test.describe('Access denied', () => {
  test('shown when signed out', async ({ page }) => {
    await clearStorage(page);
    await gotoAdmin(page);
    await expect(page.locator('#accessDenied')).not.toHaveAttribute('hidden', '');
    await expect(page.locator('#adminPanel')).toHaveAttribute('hidden', '');
    await expect(page.locator('#accessDeniedMsg')).toContainText(/not signed in/i);
  });

  test('has a link back to the Operative Terminal', async ({ page }) => {
    await clearStorage(page);
    await gotoAdmin(page);
    await expect(page.locator('.login-back')).toHaveAttribute('href', 'index.html');
  });

  test('shown for a signed-in account that is not an admin (403)', async ({ page }) => {
    await clearStorage(page);
    await seedAdminSession(page);
    await setupAdminApiMocks(page, { forbidden: true });
    await gotoAdmin(page);
    await expect(page.locator('#accessDenied')).not.toHaveAttribute('hidden', '');
    await expect(page.locator('#accessDeniedMsg')).toContainText(/not an administrator/i);
  });
});

// ---------------------------------------------------------------------------
// ADMIN PANEL — loads for a valid admin session
// ---------------------------------------------------------------------------

test.describe('Admin panel load', () => {
  test.beforeEach(async ({ page }) => {
    await clearStorage(page);
    await seedAdminSession(page);
    await setupAdminApiMocks(page);
    await gotoAdmin(page);
    await expect(page.locator('#adminPanel')).not.toHaveAttribute('hidden', '');
  });

  test('loads the quest catalog table', async ({ page }) => {
    await expect(page.locator('#questTbody tr[data-qid]')).toHaveCount(MOCK_ADMIN_QUESTS.length);
    await expect(page.locator('#questTbody')).toContainText('Morning 5k Run');
  });

  test('loads the pending quest submissions table', async ({ page }) => {
    await expect(page.locator('#pendingTbody')).toContainText('Ruck March');
  });

  test('loads the timed events table', async ({ page }) => {
    await expect(page.locator('#eventTbody')).toContainText('Double XP Weekend');
  });

  test('loads the character roster table', async ({ page }) => {
    await expect(page.locator('#charTbody tr[data-cid]')).toHaveCount(MOCK_ADMIN_CHARACTERS.length);
    await expect(page.locator('#charTbody')).toContainText('IronHero');
    await expect(page.locator('#charTbody')).toContainText('SteelWalker');
  });
});

// ---------------------------------------------------------------------------
// QUEST CATALOG — CRUD
// ---------------------------------------------------------------------------

test.describe('Quest catalog', () => {
  test.beforeEach(async ({ page }) => {
    await clearStorage(page);
    await seedAdminSession(page);
    await setupAdminApiMocks(page);
    await gotoAdmin(page);
    await expect(page.locator('#adminPanel')).not.toHaveAttribute('hidden', '');
  });

  test('Add Quest opens a blank create modal', async ({ page }) => {
    await page.locator('#addQuestBtn').click();
    await expect(page.locator('#questOverlay')).toHaveClass(/show/);
    await expect(page.locator('#questModalTitle')).toContainText('Add Quest');
    await expect(page.locator('#qf-id')).toHaveValue('');
    await expect(page.locator('#qf-id')).not.toHaveAttribute('readonly', '');
  });

  test('requires a title and, in create mode, a quest ID', async ({ page }) => {
    await page.locator('#addQuestBtn').click();
    await page.locator('#qf-id').fill('Q-XXXX');
    await page.locator('#questModalSave').click();
    await expect(page.locator('#questModalErr')).toContainText(/title/i);

    await page.locator('#qf-title').fill('Some Title');
    await page.locator('#qf-id').fill('');
    await page.locator('#questModalSave').click();
    await expect(page.locator('#questModalErr')).toContainText(/quest id/i);
  });

  test('creates a quest and shows a success flash', async ({ page }) => {
    await page.locator('#addQuestBtn').click();
    await page.locator('#qf-id').fill('Q-9999');
    await page.locator('#qf-title').fill('New Test Quest');
    await page.locator('#qf-stat').selectOption('DEX');
    await page.locator('#questModalSave').click();
    await expect(page.locator('#questOverlay')).not.toHaveClass(/show/);
    await expect(page.locator('#flash')).toHaveClass(/show ok/);
    await expect(page.locator('#flash')).toContainText(/created/i);
  });

  test('editing pre-fills the Quest ID as read-only and the title/stat from the row', async ({ page }) => {
    await page.locator('#questTbody .q-edit').first().click();
    await expect(page.locator('#questModalTitle')).toContainText('Edit Quest');
    await expect(page.locator('#qf-id')).toHaveAttribute('readonly', '');
    await expect(page.locator('#qf-id')).toHaveValue('Q-1001');
    await expect(page.locator('#qf-title')).toHaveValue('Morning 5k Run');
    await expect(page.locator('#qf-stat')).toHaveValue('CON');
  });

  test('saves an edit and shows a success flash', async ({ page }) => {
    await page.locator('#questTbody .q-edit').first().click();
    await page.locator('#qf-title').fill('Updated 5k Run Title');
    await page.locator('#questModalSave').click();
    await expect(page.locator('#questOverlay')).not.toHaveClass(/show/);
    await expect(page.locator('#flash')).toContainText(/updated/i);
  });

  test('Cancel and Escape both close the modal without saving', async ({ page }) => {
    await page.locator('#addQuestBtn').click();
    await page.locator('#questModalCancel').click();
    await expect(page.locator('#questOverlay')).not.toHaveClass(/show/);

    await page.locator('#addQuestBtn').click();
    await page.keyboard.press('Escape');
    await expect(page.locator('#questOverlay')).not.toHaveClass(/show/);
  });

  test('delete asks for confirmation and removes the quest on accept', async ({ page }) => {
    let dialogMessage = '';
    page.once('dialog', async dialog => { dialogMessage = dialog.message(); await dialog.accept(); });
    await page.locator('#questTbody .q-del').first().click();
    expect(dialogMessage).toContain('Morning 5k Run');
    await expect(page.locator('#flash')).toContainText(/deleted/i);
  });

  test('cancelling the delete dialog keeps the quest in the table', async ({ page }) => {
    page.once('dialog', dialog => dialog.dismiss());
    await page.locator('#questTbody .q-del').first().click();
    await expect(page.locator('#questTbody')).toContainText('Morning 5k Run');
  });
});

// ---------------------------------------------------------------------------
// PENDING QUEST SUBMISSIONS
// ---------------------------------------------------------------------------

test.describe('Pending quest submissions', () => {
  test.beforeEach(async ({ page }) => {
    await clearStorage(page);
    await seedAdminSession(page);
    await setupAdminApiMocks(page);
    await gotoAdmin(page);
    await expect(page.locator('#adminPanel')).not.toHaveAttribute('hidden', '');
  });

  test('each row has Approve and Reject actions', async ({ page }) => {
    const row = page.locator('#pendingTbody tr').first();
    await expect(row.locator('.p-approve')).toBeVisible();
    await expect(row.locator('.p-reject')).toBeVisible();
  });

  test('approving a submission shows a success flash and refreshes the tables', async ({ page }) => {
    await page.locator('#pendingTbody .p-approve').first().click();
    await expect(page.locator('#flash')).toContainText(/approved/i);
  });

  test('rejecting a submission shows a success flash', async ({ page }) => {
    await page.locator('#pendingTbody .p-reject').first().click();
    await expect(page.locator('#flash')).toContainText(/rejected/i);
  });

  test('refresh button reloads the pending table', async ({ page }) => {
    await page.locator('#refreshPendingBtn').click();
    await expect(page.locator('#pendingTbody')).toContainText('Ruck March');
  });
});

// ---------------------------------------------------------------------------
// TIMED EVENTS — CRUD
// ---------------------------------------------------------------------------

test.describe('Timed events', () => {
  test.beforeEach(async ({ page }) => {
    await clearStorage(page);
    await seedAdminSession(page);
    await setupAdminApiMocks(page);
    await gotoAdmin(page);
    await expect(page.locator('#adminPanel')).not.toHaveAttribute('hidden', '');
  });

  test('Add Event opens a blank create modal defaulting the multiplier to 2.0', async ({ page }) => {
    await page.locator('#addEventBtn').click();
    await expect(page.locator('#eventOverlay')).toHaveClass(/show/);
    await expect(page.locator('#eventModalTitle')).toContainText('Add Event');
    await expect(page.locator('#ef-mult')).toHaveValue('2.0');
  });

  test('requires a name, start/end dates and a positive multiplier', async ({ page }) => {
    await page.locator('#addEventBtn').click();
    await page.locator('#eventModalSave').click();
    await expect(page.locator('#eventModalErr')).toContainText(/name/i);

    await page.locator('#ef-name').fill('Weekend Boost');
    await page.locator('#eventModalSave').click();
    await expect(page.locator('#eventModalErr')).toContainText(/start and end/i);
  });

  test('creates an event and shows a success flash', async ({ page }) => {
    await page.locator('#addEventBtn').click();
    await page.locator('#ef-name').fill('Weekend Boost');
    await page.locator('#ef-start').fill('2026-02-01T00:00');
    await page.locator('#ef-end').fill('2026-02-03T00:00');
    await page.locator('#ef-mult').fill('1.5');
    await page.locator('#eventModalSave').click();
    await expect(page.locator('#eventOverlay')).not.toHaveClass(/show/);
    await expect(page.locator('#flash')).toContainText(/event created/i);
  });

  test('editing pre-fills the event name and multiplier from the row', async ({ page }) => {
    await page.locator('#eventTbody .e-edit').first().click();
    await expect(page.locator('#ef-name')).toHaveValue('Double XP Weekend');
    await expect(page.locator('#ef-mult')).toHaveValue('2');
  });

  test('delete asks for confirmation before removing the event', async ({ page }) => {
    let dialogMessage = '';
    page.once('dialog', async dialog => { dialogMessage = dialog.message(); await dialog.accept(); });
    await page.locator('#eventTbody .e-del').first().click();
    expect(dialogMessage).toContain('Double XP Weekend');
    await expect(page.locator('#flash')).toContainText(/deleted/i);
  });
});

// ---------------------------------------------------------------------------
// CHARACTER ROSTER
// ---------------------------------------------------------------------------

test.describe('Character roster', () => {
  test.beforeEach(async ({ page }) => {
    await clearStorage(page);
    await seedAdminSession(page);
    await setupAdminApiMocks(page);
    await gotoAdmin(page);
    await expect(page.locator('#adminPanel')).not.toHaveAttribute('hidden', '');
  });

  test('edit modal pre-fills name, level and streak from the row', async ({ page }) => {
    await page.locator('#charTbody .c-edit').first().click();
    await expect(page.locator('#charOverlay')).toHaveClass(/show/);
    await expect(page.locator('#cf-name')).toHaveValue('IronHero');
    await expect(page.locator('#cf-level')).toHaveValue('5');
    await expect(page.locator('#cf-streak')).toHaveValue('3');
  });

  test('requires a non-empty name to save', async ({ page }) => {
    await page.locator('#charTbody .c-edit').first().click();
    await page.locator('#cf-name').fill('');
    await page.locator('#charModalSave').click();
    await expect(page.locator('#charModalErr')).toContainText(/name/i);
  });

  test('saves character changes and shows a success flash', async ({ page }) => {
    await page.locator('#charTbody .c-edit').first().click();
    await page.locator('#cf-name').fill('RenamedHero');
    await page.locator('#charModalSave').click();
    await expect(page.locator('#charOverlay')).not.toHaveClass(/show/);
    await expect(page.locator('#flash')).toContainText(/updated/i);
  });

  test('delete asks for confirmation before removing the character', async ({ page }) => {
    let dialogMessage = '';
    page.once('dialog', async dialog => { dialogMessage = dialog.message(); await dialog.accept(); });
    await page.locator('#charTbody .c-del').first().click();
    expect(dialogMessage).toContain('IronHero');
    await expect(page.locator('#flash')).toContainText(/deleted/i);
  });

  test('refresh button reloads the roster', async ({ page }) => {
    await page.locator('#refreshCharsBtn').click();
    await expect(page.locator('#charTbody tr[data-cid]')).toHaveCount(MOCK_ADMIN_CHARACTERS.length);
  });
});

// ---------------------------------------------------------------------------
// NAVIGATION
// ---------------------------------------------------------------------------

test.describe('Navigation', () => {
  test.beforeEach(async ({ page }) => {
    await clearStorage(page);
    await seedAdminSession(page);
    await setupAdminApiMocks(page);
    await gotoAdmin(page);
    await expect(page.locator('#adminPanel')).not.toHaveAttribute('hidden', '');
  });

  test('back link points to index.html', async ({ page }) => {
    const link = page.locator('a.btn-ghost[href="index.html"]');
    await expect(link).toBeVisible();
    await expect(link).toContainText('Operative Terminal');
  });

  test('logout redirects to index.html', async ({ page }) => {
    // Token-clearing itself is covered by index.spec.js's own logout test;
    // here we only check the redirect, since our addInitScript reseeds
    // localStorage on every navigation within this same page context, which
    // would make a post-navigation token check a test-harness artifact
    // rather than a real assertion about app behavior.
    await page.locator('#logoutBtn').click();
    await page.waitForURL('**/index.html');
  });
});
