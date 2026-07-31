import { expect, test } from '@playwright/test';
import { loginAs } from './helpers/auth';

/**
 * Golden path per TZ-02-WORKFLOW §12: log in as coordinator, start the seeded demo process
 * (see scripts/seed.mjs), see its task appear, complete it, and confirm the instance completes
 * with a populated event history. Requires the full stack running via docker compose and the demo
 * process seeded (`npm run seed`).
 */
test.describe('golden path', () => {
  test('coordinator starts a process, completes its task, and sees it finish', async ({ page }) => {
    await loginAs(page, 'coordinator1', 'coordinator123');

    await expect(page.getByRole('heading', { name: 'Реестр процессов' })).toBeVisible();

    await page.getByRole('button', { name: 'Запустить процесс' }).click();
    await page.getByLabel('Шаблон процесса').click();
    await page.getByRole('option').first().click();

    const businessKey = `E2E-${Date.now()}`;
    await page.getByLabel('Бизнес-ключ').fill(businessKey);
    await page.getByLabel('Атрибуты (JSON)').fill('{"requestType":"COMPLEX"}');
    await page.getByRole('button', { name: 'Запустить', exact: true }).click();

    await expect(page.getByText(businessKey)).toBeVisible();
    await expect(page.getByText('Выполняется')).toBeVisible();

    await page.getByRole('button', { name: 'Задачи' }).click();
    const taskRow = page.getByRole('row').filter({ hasText: 'EXPERT_REVIEW' }).first();
    await expect(taskRow).toBeVisible();
    await taskRow.getByRole('button', { name: 'Выполнить' }).click();

    await page.getByRole('button', { name: 'Процессы' }).click();
    await page.getByRole('row').filter({ hasText: businessKey }).click();
    await expect(page.getByText('Завершён')).toBeVisible();
    await expect(page.getByText('TaskCompleted')).toBeVisible();
  });
});
