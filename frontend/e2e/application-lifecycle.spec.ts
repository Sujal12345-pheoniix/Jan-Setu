import { expect, test } from '@playwright/test';
import { asUser, DEMO_DOMAIN, pdf, requirePassword, uniqueEmail } from './helpers';

/**
 * Full application lifecycle against the real backend:
 * register → apply (form, documents, declaration) → submit → officer requests clarification →
 * citizen responds → officer verifies & recommends → reviewer approves & completes → citizen sees outcome.
 */
test.describe.serial('application lifecycle', () => {
  const citizenEmail = uniqueEmail('e2e.citizen');
  const citizenPassword = 'E2e-Synthetic-Pass-2026';
  let applicationUrl = '';
  let reference = '';

  test.beforeAll(() => requirePassword());

  test('citizen registers, applies and submits', async ({ page }) => {
    await page.goto('/register');
    await page.getByLabel('Full name').fill('E2E Citizen');
    await page.getByLabel(/^Email/).fill(citizenEmail);
    await page.getByLabel(/^Password/).fill(citizenPassword);
    await page.getByLabel('Confirm password').fill(citizenPassword);
    await page.getByRole('checkbox').check();
    await page.getByRole('button', { name: 'Create account' }).click();
    await expect(page.getByText('Registration complete')).toBeVisible();

    await page.getByLabel('Email').fill(citizenEmail);
    await page.getByLabel('Password').fill(citizenPassword);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);

    await page.getByRole('link', { name: 'Browse services' }).first().click();
    await page.getByLabel('Search services').fill('income');
    await page.getByRole('link', { name: 'Income Certificate (Simulated)' }).click();
    await expect(page.getByRole('heading', { name: 'Required documents' })).toBeVisible();
    await page.getByRole('button', { name: 'Start application' }).click();
    await expect(page).toHaveURL(/\/my\/applications\//);
    applicationUrl = new URL(page.url()).pathname;

    // Step 1: dynamic form (drafts may be incomplete; required fields are enforced at submission)
    await page.getByLabel(/Applicant name/).fill('E2E Citizen');
    await page.getByLabel(/Annual household income/).fill('180000');
    await page.getByLabel(/Primary income source/).selectOption('Salary');
    await page.getByRole('button', { name: 'Save and continue' }).click();

    // Step 2: documents (validated by type and magic bytes on the server)
    await expect(page.getByRole('heading', { name: 'Supporting documents' })).toBeVisible();
    const inputs = page.locator('input[type=file]');
    await inputs.nth(0).setInputFiles(pdf('identity.pdf'));
    await expect(page.getByText(/Identity proof \(synthetic\) uploaded/)).toBeVisible();
    await page.locator('input[type=file]').nth(1).setInputFiles(pdf('income.pdf'));
    await expect(page.getByText(/Income evidence \(synthetic\) uploaded/)).toBeVisible();
    await page.getByRole('button', { name: 'Continue to review' }).click();

    // Step 3: review, declaration and submit
    await page.getByRole('checkbox', { name: /information is true/i }).check();
    await page.getByRole('button', { name: 'Submit application' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Submit' }).click();
    const ref = page.getByTestId('reference-number');
    await expect(ref).toHaveText(/JAN-\d{4}-\d{6}/);
    reference = (await ref.textContent()) ?? '';
  });

  test('officer requests clarification', async ({ browser }) => {
    const { context, page } = await asUser(browser, `revenue.officer@${DEMO_DOMAIN}`);
    await page.goto('/work/tasks?tab=unassigned');
    await expect(page.getByRole('link', { name: reference })).toBeVisible();
    await page.goto(applicationUrl.replace('/my/', '/work/'));
    await page.getByRole('button', { name: 'Start review' }).click();
    await expect(page.getByText('Under review').first()).toBeVisible();
    await page.getByRole('button', { name: 'Request clarification' }).click();
    await page.getByRole('dialog').getByLabel(/What does the applicant need/).fill('Please confirm the income period covered by the evidence.');
    await page.getByRole('dialog').getByRole('button', { name: 'Send request' }).click();
    await expect(page.getByText('Clarification required').first()).toBeVisible();
    await context.close();
  });

  test('citizen responds to the clarification', async ({ browser }) => {
    const context = await browser.newContext();
    const page = await context.newPage();
    await page.goto('/login');
    await page.getByLabel('Email').fill(citizenEmail);
    await page.getByLabel('Password').fill(citizenPassword);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);
    await page.getByRole('link', { name: /Notifications/ }).first().click();
    await expect(page.getByText('Clarification requested').first()).toBeVisible();
    await page.goto(applicationUrl);
    // shown in the clarification card (and again in the timeline)
    await expect(page.getByText('Please confirm the income period covered by the evidence.').first()).toBeVisible();
    await page.getByLabel('Your response').fill('The evidence covers April 2025 to March 2026.');
    await page.getByRole('button', { name: 'Send response' }).click();
    await expect(page.getByText('Resubmitted').first()).toBeVisible();
    await context.close();
  });

  test('officer verifies documents and recommends', async ({ browser }) => {
    const { context, page } = await asUser(browser, `revenue.officer@${DEMO_DOMAIN}`);
    await page.goto(applicationUrl.replace('/my/', '/work/'));
    await page.getByRole('button', { name: 'Start review' }).click();
    await expect(page.getByRole('button', { name: 'Recommend for approval' })).toBeVisible();
    for (let i = 0; i < 2; i++) {
      await page.getByRole('button', { name: 'Mark verified' }).first().click();
      await expect(page.getByText('Document verified.').first()).toBeVisible();
    }
    await page.getByRole('button', { name: 'Recommend for approval' }).click();
    await page.getByRole('dialog').getByLabel('Remarks for the reviewer').fill('Income evidence verified.');
    await page.getByRole('dialog').getByRole('button', { name: 'Recommend' }).click();
    await expect(page.getByText('Pending approval').first()).toBeVisible();
    // separation of duties: the recommending officer has no approve action
    await expect(page.getByRole('button', { name: 'Approve' })).toHaveCount(0);
    await context.close();
  });

  test('reviewer approves and completes; citizen sees the decision', async ({ browser }) => {
    const { context, page } = await asUser(browser, `revenue.reviewer@${DEMO_DOMAIN}`);
    await page.goto(applicationUrl.replace('/my/', '/work/'));
    await page.getByRole('button', { name: 'Approve' }).click();
    await page.getByRole('dialog').getByLabel('Decision remarks').fill('Approved for demonstration.');
    await page.getByRole('dialog').getByRole('button', { name: 'Approve' }).click();
    await expect(page.getByText('Approved').first()).toBeVisible();
    await page.getByRole('button', { name: 'Mark completed' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Complete' }).click();
    await expect(page.getByText('Completed').first()).toBeVisible();
    await context.close();

    const citizen = await browser.newContext();
    const cp = await citizen.newPage();
    await cp.goto('/login');
    await cp.getByLabel('Email').fill(citizenEmail);
    await cp.getByLabel('Password').fill(citizenPassword);
    await cp.getByRole('button', { name: 'Sign in' }).click();
    await cp.goto(applicationUrl);
    await expect(cp.getByRole('heading', { name: 'Decision' })).toBeVisible();
    await expect(cp.getByText('Approved for demonstration.')).toBeVisible();
    // internal recommendation remarks never reach the citizen
    await expect(cp.getByText('Income evidence verified.')).toHaveCount(0);
    await citizen.close();
  });
});
