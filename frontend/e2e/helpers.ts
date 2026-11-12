import { expect, type Browser, type Page } from '@playwright/test';

/** Password of the seeded demo accounts (the backend's SEED_DEMO_PASSWORD). Never hard-coded. */
export const DEMO_PASSWORD = process.env.E2E_PASSWORD ?? '';
export const DEMO_DOMAIN = 'demo.jansetu.test';

export function requirePassword() {
  if (!DEMO_PASSWORD) throw new Error('Set E2E_PASSWORD to the backend SEED_DEMO_PASSWORD before running e2e tests.');
}

/** A small synthetic PDF (magic bytes are validated by the server). */
export function pdf(name: string) {
  return { name, mimeType: 'application/pdf', buffer: Buffer.from(`%PDF-1.4\n% JanSetu synthetic e2e file ${name}\n%%EOF\n`) };
}

export async function login(page: Page, email: string, password = DEMO_PASSWORD) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: false }).first().fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/dashboard/);
}

export async function asUser(browser: Browser, email: string) {
  const context = await browser.newContext();
  const page = await context.newPage();
  await login(page, email);
  return { context, page };
}

export function uniqueEmail(prefix: string) {
  return `${prefix}.${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}@example.test`;
}
