import { expect, type Page } from '@playwright/test';

/**
 * Starts the seeded demo template (see scripts/seed.mjs) with the attributes the demo routing rule
 * matches on, and returns the business key it was started under.
 */
export async function startDemoProcess(page: Page): Promise<string> {
  await page.getByRole('button', { name: 'Процессы' }).click();
  await page.getByRole('button', { name: 'Запустить процесс' }).click();
  await page.getByLabel('Шаблон процесса').click();
  await page.getByRole('option').first().click();

  const businessKey = `E2E-${Date.now()}`;
  // exact, otherwise this also matches the list page's "Поиск по бизнес-ключу" filter behind the dialog
  await page.getByLabel('Бизнес-ключ', { exact: true }).fill(businessKey);
  await page.getByLabel('Атрибуты (JSON)').fill('{"requestType":"COMPLEX"}');
  await page.getByRole('button', { name: 'Запустить', exact: true }).click();

  await expect(page.getByText(businessKey).first()).toBeVisible();
  return businessKey;
}

/**
 * Opens the task list and narrows it to freshly created tasks: the list holds every task in the
 * system, so a stack that already ran through this flow would otherwise hand back an older row.
 */
export async function openNewestCreatedTaskRow(page: Page) {
  await page.getByRole('button', { name: 'Задачи' }).click();
  await page.getByLabel('Статус').click();
  await page.getByRole('option', { name: 'Создана' }).click();
  return page.getByRole('row').filter({ hasText: 'EXPERT_REVIEW' }).last();
}
