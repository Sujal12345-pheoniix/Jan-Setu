import { expect, test } from '@playwright/test';
import { DEMO_DOMAIN, login, requirePassword } from './helpers';

/** Runs in the mobile project (Pixel 7 viewport). */
test.beforeAll(() => requirePassword());

async function noHorizontalOverflow(page: import('@playwright/test').Page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(1);
}

test('public pages fit a phone screen', async ({ page }) => {
  await page.goto('/');
  await noHorizontalOverflow(page);
  await page.goto('/services');
  await expect(page.getByRole('heading', { name: 'Service catalogue' })).toBeVisible();
  await noHorizontalOverflow(page);
});

test('the app navigation opens as a drawer on mobile', async ({ page }) => {
  await login(page, `citizen.one@${DEMO_DOMAIN}`);
  await noHorizontalOverflow(page);
  await page.getByRole('button', { name: 'Open navigation' }).click();
  await page.getByRole('dialog', { name: 'Navigation' }).getByRole('link', { name: 'My applications' }).click();
  await expect(page.getByRole('heading', { name: 'My applications' })).toBeVisible();
  await noHorizontalOverflow(page);
});
