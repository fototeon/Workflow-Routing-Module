import { expect, test } from '@playwright/test';
import { loginAs } from './helpers/auth';
import { openNewestCreatedTaskRow, startDemoProcess } from './helpers/process';

/** UI coverage for the requirements added on top of the golden path (TZ §2, §3, §9, REQ-02-008/009). */
test.describe('process actions', () => {
  test('manager suspends and resumes a process', async ({ page }) => {
    await loginAs(page, 'manager1', 'manager123');
    const businessKey = await startDemoProcess(page);

    await page.getByRole('button', { name: 'Приостановить' }).click();
    await page.getByLabel('Причина').fill('Ожидание документов от заявителя');
    await page.getByRole('button', { name: 'Приостановить', exact: true }).last().click();
    await expect(page.getByText('Приостановлен').first()).toBeVisible();

    await page.getByRole('button', { name: 'Возобновить' }).click();
    await expect(page.getByText('Выполняется').first()).toBeVisible();

    await expect(page.getByText('ProcessSuspended')).toBeVisible();
    await expect(page.getByText('ProcessResumed')).toBeVisible();
    expect(businessKey).toContain('E2E-');
  });

  test('coordinator starts a sub-process and can walk back to the parent', async ({ page }) => {
    await loginAs(page, 'coordinator1', 'coordinator123');
    const parentKey = await startDemoProcess(page);

    await page.getByRole('button', { name: 'Запустить подпроцесс' }).click();
    await page.getByLabel('Шаблон подпроцесса').click();
    await page.getByRole('option', { name: /DEMO_SUBPROCESS_CHECK/ }).first().click();
    const childKey = `${parentKey}-SUB`;
    await page.getByLabel('Бизнес-ключ', { exact: true }).fill(childKey);
    await page.getByRole('button', { name: 'Запустить', exact: true }).click();

    await expect(page.getByText(childKey).first()).toBeVisible();
    await page.getByRole('button', { name: 'Родительский процесс' }).click();
    await expect(page.getByRole('heading', { name: parentKey })).toBeVisible();
  });

  test('the card shows the route map of the template', async ({ page }) => {
    await loginAs(page, 'coordinator1', 'coordinator123');
    await startDemoProcess(page);

    await expect(page.getByRole('img', { name: 'Карта маршрута процесса' })).toBeVisible();
    await expect(page.getByText('EXPERT_REVIEW').first()).toBeVisible();
  });
});

test.describe('task list', () => {
  test('coordinator completes several tasks at once', async ({ page }) => {
    await loginAs(page, 'coordinator1', 'coordinator123');
    await startDemoProcess(page);
    await startDemoProcess(page);

    const row = await openNewestCreatedTaskRow(page);
    await expect(row).toBeVisible();

    await page.getByLabel('Выбрать все задачи').check();
    await page.getByRole('button', { name: /Выполнить выбранные/ }).click();

    await expect(page.getByRole('row').filter({ hasText: 'EXPERT_REVIEW' })).toHaveCount(0);
  });
});

test.describe('analytics', () => {
  test('analyst sees the dashboard and can export CSV', async ({ page }) => {
    await loginAs(page, 'analyst1', 'analyst123');

    await page.getByRole('button', { name: 'Аналитика' }).click();
    await expect(page.getByText('Всего процессов')).toBeVisible();
    await expect(page.getByText('Процессы по статусам')).toBeVisible();
    await expect(page.getByText('Соблюдение SLA')).toBeVisible();

    const download = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Выгрузить процессы (CSV)' }).click();
    expect((await download).suggestedFilename()).toBe('process-instances.csv');
  });

  test('coordinator has no analytics entry in the menu', async ({ page }) => {
    await loginAs(page, 'coordinator1', 'coordinator123');
    await expect(page.getByRole('button', { name: 'Аналитика' })).not.toBeVisible();
  });
});

test.describe('saved views', () => {
  test('a filter set can be stored and applied again', async ({ page }) => {
    await loginAs(page, 'coordinator1', 'coordinator123');

    await page.getByLabel('Поиск по бизнес-ключу').fill('REQ-2026-001');
    await page.getByRole('button', { name: 'Сохранить представление' }).click();
    await page.getByLabel('Название').fill('Моя заявка');
    await page.getByRole('button', { name: 'Сохранить', exact: true }).click();

    await page.getByLabel('Поиск по бизнес-ключу').fill('');
    await page.getByRole('button', { name: 'Моя заявка' }).click();
    await expect(page.getByLabel('Поиск по бизнес-ключу')).toHaveValue('REQ-2026-001');
  });
});
