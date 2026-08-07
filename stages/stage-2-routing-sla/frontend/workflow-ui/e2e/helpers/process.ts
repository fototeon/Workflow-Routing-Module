import { expect, type Page } from '@playwright/test';

/** The template `scripts/seed.mjs` creates for the golden path. */
export const DEMO_TEMPLATE_CODE = 'DEMO_EXPERTISE_REVIEW';

/**
 * Starts the seeded demo template (see scripts/seed.mjs) with the attributes its routing rule
 * matches on, and returns the business key it was started under.
 */
export async function startDemoProcess(page: Page): Promise<string> {
  await page.getByRole('button', { name: 'Процессы' }).click();
  await page.getByRole('button', { name: 'Запустить процесс' }).click();
  await page.getByLabel('Шаблон процесса').click();
  // Pick by code: the seed publishes several templates, so "the first option" is not this one.
  await page.getByRole('option', { name: new RegExp(DEMO_TEMPLATE_CODE) }).first().click();

  const businessKey = `E2E-${Date.now()}`;
  // exact, otherwise this also matches the list page's "Поиск по бизнес-ключу" filter behind the dialog
  await page.getByLabel('Бизнес-ключ', { exact: true }).fill(businessKey);
  await page.getByLabel('Атрибуты (JSON)').fill('{"requestType":"COMPLEX"}');
  await page.getByRole('button', { name: 'Запустить', exact: true }).click();

  await expect(page.getByText(businessKey).first()).toBeVisible();
  return businessKey;
}

/**
 * Opens one case's card and, from there, its own task list — the shared list holds every task in
 * the system, including the seeded ones, so filtering by the case is what makes it unambiguous.
 */
export async function openTaskOfCase(page: Page, businessKey: string) {
  await page.getByRole('button', { name: 'Процессы' }).click();
  await page.getByRole('row').filter({ hasText: businessKey }).click();
  await page.getByRole('button', { name: 'Задачи заявки' }).click();
  const row = page.getByRole('row').filter({ hasText: 'EXPERT_REVIEW' });
  await expect(row).toHaveCount(1);
  return row;
}
