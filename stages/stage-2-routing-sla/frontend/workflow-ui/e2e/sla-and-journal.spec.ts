import { expect, test } from '@playwright/test';
import { loginAs } from './helpers/auth';
import { openTaskOfCase, startDemoProcess } from './helpers/process';

/** What this stage added on top of the core: SLA policies, deadlines and the process journal. */
test.describe('SLA and journal', () => {
  test('admin sees the seeded SLA policies with their escalation steps', async ({ page }) => {
    await loginAs(page, 'admin1', 'admin123');
    await page.getByRole('button', { name: 'SLA политики' }).click();

    await expect(page.getByText('DEMO_SLA').first()).toBeVisible();
    await expect(page.getByText('DEMO_SLA_FAST').first()).toBeVisible();
  });

  test('a started process gets a deadline and an event journal', async ({ page }) => {
    await loginAs(page, 'coordinator1', 'coordinator123');
    const businessKey = await startDemoProcess(page);

    // The template carries an SLA policy, so its first task is stamped with a deadline.
    const taskRow = await openTaskOfCase(page, businessKey);
    await expect(taskRow).toContainText(/\d{2}\.\d{2}\.\d{4}/);

    await page.getByRole('button', { name: 'Процессы' }).click();
    await page.getByRole('row').filter({ hasText: businessKey }).click();
    await expect(page.getByText('ProcessStarted').first()).toBeVisible();
    await expect(page.getByText('TaskCreated').first()).toBeVisible();
  });

  test('an administrator builds a routing rule from a group of conditions', async ({ page }) => {
    await loginAs(page, 'admin1', 'admin123');
    await page.getByRole('button', { name: 'Шаблоны процессов' }).click();

    const code = `E2E_TPL_${Date.now()}`;
    await page.getByLabel('Код', { exact: true }).fill(code);
    await page.getByLabel('Название', { exact: true }).fill('E2E шаблон');
    await page.getByRole('button', { name: 'Создать' }).click();
    await expect(page.getByRole('heading', { name: `${code} v1` })).toBeVisible();

    await page.getByLabel('Название правила').fill('route-complex');
    await page.getByLabel('Код целевого шага').fill('EXPERT_REVIEW');
    await page.getByLabel('Целевая роль').fill('COORDINATOR');
    await page.getByLabel('Поле').fill('requestType');
    await page.getByLabel('Значение').fill('COMPLEX');
    await page.getByRole('button', { name: 'Добавить условие' }).click();
    await page.getByLabel('Поле').nth(1).fill('amount');
    await page.getByLabel('Оператор').nth(1).click();
    await page.getByRole('option', { name: 'GTE', exact: true }).click();
    await page.getByLabel('Значение').nth(1).fill('1000');
    await page.getByRole('button', { name: 'Добавить правило' }).click();

    // Both conditions end up in one AND group on the stored rule.
    await expect(page.getByText('"op":"AND"')).toBeVisible();

    await page.getByRole('button', { name: 'Опубликовать' }).click();
    await expect(page.getByText('Опубликован').first()).toBeVisible();

    // Reopening the template reloads its configuration journal, publication included.
    await page.getByRole('button', { name: new RegExp(code) }).click();
    await expect(page.getByText('ProcessDefinitionPublished')).toBeVisible();
  });
});
