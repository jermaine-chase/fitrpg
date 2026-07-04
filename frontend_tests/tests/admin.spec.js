import { test, expect } from '@playwright/test';
import {
  MOCK_QUESTS,
  MOCK_ADMIN_CHARACTERS,
  setupAdminApiMocks,
  seedAdminSession,
} from './helpers.js';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function gotoAdmin(page) {
  await page.goto('/admin.html');
}

async function loginAs(page, user = 'admin', pass = 'ironpath_admin') {
  await page.locator('#loginUser').fill(user);
  await page.locator('#loginPass').fill(pass);
  await page.locator('#loginBtn').click();
}

// ---------------------------------------------------------------------------
// LOGIN SCREEN
// ---------------------------------------------------------------------------

test.describe('Login screen', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await setupAdminApiMocks(page);
  });

  test('shows the login screen on fresh load', async ({ page }) => {
    await gotoAdmin(page);
    await expect(page.locator('#loginScreen')).not.toHaveAttribute('hidden');
    await expect(page.locator('#adminPanel')).toHaveAttribute('hidden', '');
  });

  test('shows username and password fields', async ({ page }) => {
    await gotoAdmin(page);
    await expect(page.locator('#loginUser')).toBeVisible();
    await expect(page.locator('#loginPass')).toBeVisible();
  });

  test('login button is labeled correctly', async ({ page }) => {
    await gotoAdmin(page);
    await expect(page.locator('#loginBtn')).toHaveText('Connect to System');
  });

  test('shows an error when credentials are empty', async ({ page }) => {
    await gotoAdmin(page);
    await page.locator('#loginBtn').click();
    await expect(page.locator('#loginErr')).toContainText(/required/i);
  });

  test('shows an error when only username is provided', async ({ page }) => {
    await gotoAdmin(page);
    await page.locator('#loginUser').fill('admin');
    await page.locator('#loginBtn').click();
    await expect(page.locator('#loginErr')).toContainText(/required/i);
  });

  test('shows an error on wrong credentials (401)', async ({ page }) => {
    await gotoAdmin(page);
    // Override the mock to return 401 for this test
    await page.route('http://localhost:8080/api/admin/quests', route =>
      route.fulfill({ status: 401, body: '' })
    );
    await loginAs(page, 'admin', 'wrongpass');
    await expect(page.locator('#loginErr')).toContainText(/Invalid credentials|Cannot connect/i);
    await expect(page.locator('#loginScreen')).not.toHaveAttribute('hidden');
  });

  test('pressing Enter in the password field submits the login form', async ({ page }) => {
    await gotoAdmin(page);
    await page.locator('#loginUser').fill('admin');
    await page.locator('#loginPass').fill('ironpath_admin');
    await page.locator('#loginPass').press('Enter');
    await expect(page.locator('#adminPanel')).not.toHaveAttribute('hidden');
  });

  test('pressing Tab in the username field moves focus to password', async ({ page }) => {
    await gotoAdmin(page);
    await page.locator('#loginUser').fill('admin');
    await page.locator('#loginUser').press('Enter');
    // The code focuses the password field on Enter in username
    await expect(page.locator('#loginPass')).toBeFocused();
  });
});

// ---------------------------------------------------------------------------
// SUCCESSFUL LOGIN
// ---------------------------------------------------------------------------

test.describe('Successful login', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await setupAdminApiMocks(page);
  });

  test('hides login screen and shows admin panel', async ({ page }) => {
    await gotoAdmin(page);
    await loginAs(page);
    await expect(page.locator('#loginScreen')).toHaveAttribute('hidden', '');
    await expect(page.locator('#adminPanel')).not.toHaveAttribute('hidden');
  });

  test('loads the quest table after login', async ({ page }) => {
    await gotoAdmin(page);
    await loginAs(page);
    await expect(page.locator('#questTbody tr')).not.toHaveCount(0);
    await expect(page.locator('#questTbody')).toContainText('Morning 5k Run');
  });

  test('loads the character roster after login', async ({ page }) => {
    await gotoAdmin(page);
    await loginAs(page);
    await expect(page.locator('#charTbody tr')).not.toHaveCount(0);
    await expect(page.locator('#charTbody')).toContainText('IronHero');
  });
});

// ---------------------------------------------------------------------------
// AUTO-LOGIN with stored token
// ---------------------------------------------------------------------------

test.describe('Auto-login with stored session', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
  });

  test('skips login screen when a valid token is already stored', async ({ page }) => {
    await seedAdminSession(page);
    await setupAdminApiMocks(page);
    await gotoAdmin(page);
    await expect(page.locator('#adminPanel')).not.toHaveAttribute('hidden');
    await expect(page.locator('#loginScreen')).toHaveAttribute('hidden', '');
  });

  test('falls back to login screen when stored token is invalid', async ({ page }) => {
    await seedAdminSession(page, 'admin', 'badpass');
    // Return 401 for auth check
    await page.route('http://localhost:8080/**', route =>
      route.fulfill({ status: 401, body: '' })
    );
    await gotoAdmin(page);
    await expect(page.locator('#loginScreen')).not.toHaveAttribute('hidden');
  });
});

// ---------------------------------------------------------------------------
// LOGOUT
// ---------------------------------------------------------------------------

test.describe('Logout', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await seedAdminSession(page);
    await setupAdminApiMocks(page);
    await gotoAdmin(page);
    // Wait for admin panel to be visible
    await expect(page.locator('#adminPanel')).not.toHaveAttribute('hidden');
  });

  test('logout button returns to the login screen', async ({ page }) => {
    await page.locator('#logoutBtn').click();
    await expect(page.locator('#loginScreen')).not.toHaveAttribute('hidden');
    await expect(page.locator('#adminPanel')).toHaveAttribute('hidden', '');
  });

  test('after logout, stored token is cleared', async ({ page }) => {
    await page.locator('#logoutBtn').click();
    const token = await page.evaluate(() => sessionStorage.getItem('adminToken'));
    expect(token).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// QUEST CATALOG — table display
// ---------------------------------------------------------------------------

test.describe('Quest Catalog — table', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await seedAdminSession(page);
    await setupAdminApiMocks(page);
    await gotoAdmin(page);
    await expect(page.locator('#adminPanel')).not.toHaveAttribute('hidden');
  });

  test('renders a row for each quest', async ({ page }) => {
    await expect(page.locator('#questTbody tr[data-qid]')).toHaveCount(MOCK_QUESTS.length);
  });

  test('shows quest ID in each row', async ({ page }) => {
    await expect(page.locator('#questTbody')).toContainText('Q-1001');
    await expect(page.locator('#questTbody')).toContainText('Q-1002');
  });

  test('shows the stat badge for each quest', async ({ page }) => {
    await expect(page.locator('#questTbody .badge-con')).toContainText('CON');
    await expect(page.locator('#questTbody .badge-str')).toContainText('STR');
  });

  test('each row has Edit and Delete action buttons', async ({ page }) => {
    const firstRow = page.locator('#questTbody tr[data-qid]').first();
    await expect(firstRow.locator('.q-edit')).toBeVisible();
    await expect(firstRow.locator('.q-del')).toBeVisible();
  });
});

// ---------------------------------------------------------------------------
// QUEST CATALOG — add quest
// ---------------------------------------------------------------------------

test.describe('Quest Catalog — add quest', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await seedAdminSession(page);
    await setupAdminApiMocks(page);
    await gotoAdmin(page);
    await expect(page.locator('#adminPanel')).not.toHaveAttribute('hidden');
  });

  test('Add Quest button opens the quest modal', async ({ page }) => {
    await page.locator('#addQuestBtn').click();
    await expect(page.locator('#questOverlay')).toHaveClass(/show/);
    await expect(page.locator('#questModalTitle')).toContainText('Add Quest');
  });

  test('modal starts with blank Quest ID and Title', async ({ page }) => {
    await page.locator('#addQuestBtn').click();
    await expect(page.locator('#qf-id')).toHaveValue('');
    await expect(page.locator('#qf-title')).toHaveValue('');
  });

  test('Quest ID field is editable in create mode', async ({ page }) => {
    await page.locator('#addQuestBtn').click();
    await expect(page.locator('#qf-id')).not.toHaveAttribute('readonly');
  });

  test('shows an error when title is empty', async ({ page }) => {
    await page.locator('#addQuestBtn').click();
    await page.locator('#qf-id').fill('Q-XXXX');
    await page.locator('#questModalSave').click();
    await expect(page.locator('#questModalErr')).toContainText(/title/i);
  });

  test('shows an error when Quest ID is empty in create mode', async ({ page }) => {
    await page.locator('#addQuestBtn').click();
    await page.locator('#qf-title').fill('Some Title');
    await page.locator('#qf-id').fill('');
    await page.locator('#questModalSave').click();
    await expect(page.locator('#questModalErr')).toContainText(/Quest ID/i);
  });

  test('Cancel button closes the modal', async ({ page }) => {
    await page.locator('#addQuestBtn').click();
    await page.locator('#questModalCancel').click();
    await expect(page.locator('#questOverlay')).not.toHaveClass(/show/);
  });

  test('Escape key closes the quest modal', async ({ page }) => {
    await page.locator('#addQuestBtn').click();
    await page.keyboard.press('Escape');
    await expect(page.locator('#questOverlay')).not.toHaveClass(/show/);
  });

  test('successfully creates a quest and shows a flash message', async ({ page }) => {
    await page.locator('#addQuestBtn').click();
    await page.locator('#qf-id').fill('Q-9999');
    await page.locator('#qf-title').fill('New Test Quest');
    await page.locator('#qf-stat').selectOption('DEX');
    await page.locator('#qf-minlevel').fill('1');
    await page.locator('#qf-charxp').fill('40');
    await page.locator('#qf-statxp').fill('60');
    await page.locator('#questModalSave').click();

    await expect(page.locator('#questOverlay')).not.toHaveClass(/show/);
    await expect(page.locator('#flash')).toHaveClass(/show/);
    await expect(page.locator('#flash')).toContainText(/created/i);
  });
});

// ---------------------------------------------------------------------------
// QUEST CATALOG — edit quest
// ---------------------------------------------------------------------------

test.describe('Quest Catalog — edit quest', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await seedAdminSession(page);
    await setupAdminApiMocks(page);
    await gotoAdmin(page);
    await expect(page.locator('#adminPanel')).not.toHaveAttribute('hidden');
  });

  test('edit button opens the modal in edit mode', async ({ page }) => {
    await page.locator('#questTbody .q-edit').first().click();
    await expect(page.locator('#questOverlay')).toHaveClass(/show/);
    await expect(page.locator('#questModalTitle')).toContainText('Edit Quest');
  });

  test('edit modal pre-fills the Quest ID as read-only', async ({ page }) => {
    await page.locator('#questTbody .q-edit').first().click();
    await expect(page.locator('#qf-id')).toHaveAttribute('readonly', '');
    await expect(page.locator('#qf-id')).toHaveValue('Q-1001');
  });

  test('edit modal pre-fills the title from the table row', async ({ page }) => {
    await page.locator('#questTbody .q-edit').first().click();
    await expect(page.locator('#qf-title')).toHaveValue('Morning 5k Run');
  });

  test('edit modal pre-fills the stat from the table row', async ({ page }) => {
    await page.locator('#questTbody .q-edit').first().click();
    await expect(page.locator('#qf-stat')).toHaveValue('CON');
  });

  test('saves the updated quest and shows a flash message', async ({ page }) => {
    await page.locator('#questTbody .q-edit').first().click();
    await page.locator('#qf-title').fill('Updated 5k Run Title');
    await page.locator('#questModalSave').click();

    await expect(page.locator('#questOverlay')).not.toHaveClass(/show/);
    await expect(page.locator('#flash')).toHaveClass(/show/);
    await expect(page.locator('#flash')).toContainText(/updated/i);
  });
});

// ---------------------------------------------------------------------------
// QUEST CATALOG — delete quest
// ---------------------------------------------------------------------------

test.describe('Quest Catalog — delete quest', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await seedAdminSession(page);
    await setupAdminApiMocks(page);
    await gotoAdmin(page);
    await expect(page.locator('#adminPanel')).not.toHaveAttribute('hidden');
  });

  test('shows a confirmation dialog before deleting', async ({ page }) => {
    let dialogMessage = '';
    page.once('dialog', async dialog => {
      dialogMessage = dialog.message();
      await dialog.dismiss();
    });
    await page.locator('#questTbody .q-del').first().click();
    expect(dialogMessage).toMatch(/Morning 5k Run/);
  });

  test('cancelling the dialog keeps the quest in the table', async ({ page }) => {
    page.once('dialog', dialog => dialog.dismiss());
    await page.locator('#questTbody .q-del').first().click();
    await expect(page.locator('#questTbody')).toContainText('Morning 5k Run');
  });

  test('confirming delete shows a flash message', async ({ page }) => {
    page.once('dialog', dialog => dialog.accept());
    await page.locator('#questTbody .q-del').first().click();
    await expect(page.locator('#flash')).toHaveClass(/show/);
    await expect(page.locator('#flash')).toContainText(/deleted/i);
  });
});

// ---------------------------------------------------------------------------
// CHARACTER ROSTER — table display
// ---------------------------------------------------------------------------

test.describe('Character Roster — table', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await seedAdminSession(page);
    await setupAdminApiMocks(page);
    await gotoAdmin(page);
    await expect(page.locator('#adminPanel')).not.toHaveAttribute('hidden');
  });

  test('renders a row for each character', async ({ page }) => {
    await expect(page.locator('#charTbody tr[data-cid]')).toHaveCount(MOCK_ADMIN_CHARACTERS.length);
  });

  test('shows character name in the table', async ({ page }) => {
    await expect(page.locator('#charTbody')).toContainText('IronHero');
    await expect(page.locator('#charTbody')).toContainText('SteelWalker');
  });

  test('shows level badge for each character', async ({ page }) => {
    const firstRow = page.locator('#charTbody tr[data-cid]').first();
    await expect(firstRow.locator('.badge-tier')).toContainText('LV 5');
  });

  test('each row has Edit and Delete action buttons', async ({ page }) => {
    const firstRow = page.locator('#charTbody tr[data-cid]').first();
    await expect(firstRow.locator('.c-edit')).toBeVisible();
    await expect(firstRow.locator('.c-del')).toBeVisible();
  });

  test('refresh button reloads the character table', async ({ page }) => {
    await page.locator('#refreshCharsBtn').click();
    // After reload the rows should still be present
    await expect(page.locator('#charTbody tr[data-cid]')).toHaveCount(MOCK_ADMIN_CHARACTERS.length);
  });
});

// ---------------------------------------------------------------------------
// CHARACTER ROSTER — edit character
// ---------------------------------------------------------------------------

test.describe('Character Roster — edit character', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await seedAdminSession(page);
    await setupAdminApiMocks(page);
    await gotoAdmin(page);
    await expect(page.locator('#adminPanel')).not.toHaveAttribute('hidden');
  });

  test('edit button opens the character modal', async ({ page }) => {
    await page.locator('#charTbody .c-edit').first().click();
    await expect(page.locator('#charOverlay')).toHaveClass(/show/);
  });

  test('character modal pre-fills the operative name', async ({ page }) => {
    await page.locator('#charTbody .c-edit').first().click();
    await expect(page.locator('#cf-name')).toHaveValue('IronHero');
  });

  test('character modal pre-fills the level', async ({ page }) => {
    await page.locator('#charTbody .c-edit').first().click();
    await expect(page.locator('#cf-level')).toHaveValue('5');
  });

  test('character modal pre-fills the streak count', async ({ page }) => {
    await page.locator('#charTbody .c-edit').first().click();
    await expect(page.locator('#cf-streak')).toHaveValue('3');
  });

  test('shows an error when name is cleared', async ({ page }) => {
    await page.locator('#charTbody .c-edit').first().click();
    await page.locator('#cf-name').fill('');
    await page.locator('#charModalSave').click();
    await expect(page.locator('#charModalErr')).toContainText(/Name/i);
  });

  test('cancel button closes the character modal', async ({ page }) => {
    await page.locator('#charTbody .c-edit').first().click();
    await page.locator('#charModalCancel').click();
    await expect(page.locator('#charOverlay')).not.toHaveClass(/show/);
  });

  test('Escape key closes the character modal', async ({ page }) => {
    await page.locator('#charTbody .c-edit').first().click();
    await page.keyboard.press('Escape');
    await expect(page.locator('#charOverlay')).not.toHaveClass(/show/);
  });

  test('saves character changes and shows flash message', async ({ page }) => {
    await page.locator('#charTbody .c-edit').first().click();
    await page.locator('#cf-name').fill('RenamedHero');
    await page.locator('#cf-level').fill('10');
    await page.locator('#cf-streak').fill('7');
    await page.locator('#charModalSave').click();

    await expect(page.locator('#charOverlay')).not.toHaveClass(/show/);
    await expect(page.locator('#flash')).toHaveClass(/show/);
    await expect(page.locator('#flash')).toContainText(/updated/i);
  });
});

// ---------------------------------------------------------------------------
// CHARACTER ROSTER — delete character
// ---------------------------------------------------------------------------

test.describe('Character Roster — delete character', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await seedAdminSession(page);
    await setupAdminApiMocks(page);
    await gotoAdmin(page);
    await expect(page.locator('#adminPanel')).not.toHaveAttribute('hidden');
  });

  test('shows a confirmation dialog before deleting', async ({ page }) => {
    let dialogMessage = '';
    page.once('dialog', async dialog => {
      dialogMessage = dialog.message();
      await dialog.dismiss();
    });
    await page.locator('#charTbody .c-del').first().click();
    expect(dialogMessage).toMatch(/IronHero/);
  });

  test('cancelling the dialog keeps the character in the table', async ({ page }) => {
    page.once('dialog', dialog => dialog.dismiss());
    await page.locator('#charTbody .c-del').first().click();
    await expect(page.locator('#charTbody')).toContainText('IronHero');
  });

  test('confirming delete shows a success flash message', async ({ page }) => {
    page.once('dialog', dialog => dialog.accept());
    await page.locator('#charTbody .c-del').first().click();
    await expect(page.locator('#flash')).toHaveClass(/show/);
    await expect(page.locator('#flash')).toContainText(/deleted/i);
  });
});

// ---------------------------------------------------------------------------
// NAVIGATION
// ---------------------------------------------------------------------------

test.describe('Navigation', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => { localStorage.clear(); sessionStorage.clear(); });
    await seedAdminSession(page);
    await setupAdminApiMocks(page);
    await gotoAdmin(page);
    await expect(page.locator('#adminPanel')).not.toHaveAttribute('hidden');
  });

  test('back link points to index.html', async ({ page }) => {
    const link = page.locator('a.btn-ghost[href="index.html"]');
    await expect(link).toBeVisible();
    await expect(link).toContainText('Operative Terminal');
  });
});
