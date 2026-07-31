import { expect, test } from '@playwright/test';
import { loginAs } from './helpers/auth';
import { openNewestCreatedTaskRow, startDemoProcess } from './helpers/process';

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

    const businessKey = await startDemoProcess(page);
    // status shows both in the chip and in the process-map stepper, hence first()
    await expect(page.getByText('Выполняется').first()).toBeVisible();

    const taskRow = await openNewestCreatedTaskRow(page);
    await expect(taskRow).toBeVisible();
    await taskRow.getByRole('button', { name: 'Выполнить' }).click();

    await page.getByRole('button', { name: 'Процессы' }).click();
    await page.getByRole('row').filter({ hasText: businessKey }).click();
    await expect(page.getByText('Завершён').first()).toBeVisible();
    await expect(page.getByText('TaskCompleted').first()).toBeVisible();
  });
});
