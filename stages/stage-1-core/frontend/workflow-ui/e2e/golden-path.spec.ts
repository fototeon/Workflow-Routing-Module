import { expect, test } from '@playwright/test';
import { loginAs } from './helpers/auth';

/** The template `scripts/seed.mjs` publishes for the golden path. */
const DEMO_TEMPLATE_CODE = 'DEMO_EXPERTISE_REVIEW';

/**
 * The golden path through the UI: a coordinator starts a process from a published template,
 * completes the task the routing rules opened, and sees the process finish.
 */
test('coordinator starts a process, completes its task, and sees it finish', async ({ page }) => {
  await loginAs(page, 'coordinator1', 'coordinator123');

  await page.getByRole('button', { name: 'Запустить процесс' }).click();
  await page.getByLabel('Шаблон процесса').click();
  await page.getByRole('option', { name: new RegExp(DEMO_TEMPLATE_CODE) }).first().click();

  const businessKey = `E2E-${Date.now()}`;
  await page.getByLabel('Бизнес-ключ', { exact: true }).fill(businessKey);
  await page.getByLabel('Атрибуты (JSON)').fill('{"requestType":"COMPLEX"}');
  await page.getByRole('button', { name: 'Запустить', exact: true }).click();

  const row = page.getByRole('row').filter({ hasText: businessKey });
  await expect(row).toBeVisible();
  await expect(row).toContainText('Выполняется');
  await expect(row).toContainText('EXPERT_REVIEW');

  // Open this case's own tasks, so the seeded ones cannot be mistaken for it.
  await row.getByRole('button', { name: 'Задачи' }).click();
  const taskRow = page.getByRole('row').filter({ hasText: 'EXPERT_REVIEW' });
  await expect(taskRow).toHaveCount(1);
  await taskRow.getByRole('button', { name: 'Выполнить' }).click();
  await expect(taskRow).toContainText('Выполнено');

  await page.getByRole('button', { name: 'Процессы' }).click();
  await page.getByLabel('Поиск по бизнес-ключу').fill(businessKey);
  await expect(page.getByRole('row').filter({ hasText: businessKey })).toContainText('Завершён');
});
