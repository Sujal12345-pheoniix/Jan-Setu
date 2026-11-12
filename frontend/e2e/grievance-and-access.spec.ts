import { expect, test } from '@playwright/test';
import { asUser, DEMO_DOMAIN, login, requirePassword } from './helpers';

test.beforeAll(() => requirePassword());

test('citizen raises a grievance; officer acknowledges and replies; internal notes stay internal', async ({ browser }) => {
  const { context, page } = await asUser(browser, `citizen.one@${DEMO_DOMAIN}`);
  await page.goto('/my/grievances/new');
  await page.getByRole('button', { name: 'Submit grievance' }).click();
  await expect(page.getByText('Choose a category')).toBeVisible();
  await page.getByLabel(/Department/).selectOption({ label: 'Revenue and Land Records (Demo)' });
  await page.getByLabel(/Category/).selectOption('SERVICE_DELAY');
  const subject = `E2E delay ${Date.now()}`;
  await page.getByLabel(/Subject/).fill(subject);
  await page.getByLabel(/Describe the issue/).fill('My synthetic certificate request has had no update for several weeks.');
  await page.getByRole('button', { name: 'Submit grievance' }).click();
  await expect(page.getByText(/GRV-\d{4}-\d{6}/).first()).toBeVisible();
  const grievanceUrl = new URL(page.url()).pathname;

  const officer = await asUser(browser, `revenue.officer@${DEMO_DOMAIN}`);
  await officer.page.goto(grievanceUrl.replace('/my/', '/work/'));
  await officer.page.getByRole('button', { name: 'Acknowledge' }).click();
  await expect(officer.page.getByRole('button', { name: 'Acknowledge' })).toHaveCount(0);
  await expect(officer.page.getByText('Grievance acknowledged.')).toBeVisible();
  await officer.page.getByLabel('Add a reply or note').fill('Internal: checked the counter register.');
  await officer.page.getByLabel('Visibility').selectOption('INTERNAL');
  await officer.page.getByRole('button', { name: 'Add internal note' }).click();
  await expect(officer.page.getByText('Internal note').first()).toBeVisible();
  await officer.page.getByLabel('Add a reply or note').fill('We are looking into your request.');
  await officer.page.getByLabel('Visibility').selectOption('PUBLIC');
  await officer.page.getByRole('button', { name: 'Send' }).click();
  await expect(officer.page.getByText('We are looking into your request.')).toBeVisible();
  await officer.context.close();

  await page.reload();
  await expect(page.getByText('We are looking into your request.')).toBeVisible();
  await expect(page.getByText('Internal: checked the counter register.')).toHaveCount(0);
  await expect(page.getByText('Acknowledged').first()).toBeVisible();
  await context.close();
});

test('anonymous users are sent to login and citizens cannot open admin pages', async ({ page }) => {
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/login/);
  await login(page, `citizen.two@${DEMO_DOMAIN}`);
  await page.goto('/admin/users');
  await expect(page.getByRole('heading', { name: 'You do not have access to this page' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Users' })).toHaveCount(0);
  await page.goto('/this-page-does-not-exist');
  await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible();
});

test('department admin creates a draft service but cannot see other departments', async ({ page }) => {
  await login(page, `revenue.admin@${DEMO_DOMAIN}`);
  await page.goto('/admin/services');
  await expect(page.getByRole('link', { name: 'Income Certificate (Simulated)' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Trade Licence (Simulated)' })).toHaveCount(0);
  await page.getByRole('link', { name: 'New service' }).click();
  await page.getByLabel(/^Code/).fill(`E2E_${Date.now().toString(36).toUpperCase()}`);
  await page.getByLabel(/^Name/).fill('E2E Draft Service');
  await page.getByLabel(/^Description/).fill('Created by the Playwright end-to-end test.');
  await page.getByRole('button', { name: 'Create draft' }).click();
  await expect(page.getByRole('heading', { name: 'E2E Draft Service' })).toBeVisible();
  await expect(page.getByText('Draft').first()).toBeVisible();
});

test('keyboard users can skip to content and reach the sign-in form', async ({ page }) => {
  await page.goto('/');
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: 'Skip to main content' })).toBeFocused();
  await page.goto('/login');
  await page.getByLabel('Email').focus();
  await page.keyboard.press('Tab');
  await expect(page.getByLabel('Password')).toBeFocused();
});
