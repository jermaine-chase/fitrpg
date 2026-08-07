import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  retries: 0,
  timeout: 20000,
  use: {
    baseURL: 'http://localhost:3001',
    headless: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'Mobile Safari', use: { ...devices['iPhone 15'] } },
    { name: 'Mobile Safari (iPad)', use: { ...devices['iPad Pro 11'] } },
  ],
  webServer: {
    command: 'npx http-server ../src/main/resources/static -p 3001 -c-1 --silent',
    port: 3001,
    reuseExistingServer: !process.env.CI,
    timeout: 15000,
  },
  reporter: [['list'], ['html', { open: 'never' }]],
});
