import { expect, test } from '@playwright/test';
import { loginAs } from './helpers/auth';
import { openNewestCreatedTaskRow, startDemoProcess } from './helpers/process';

test.describe('reassignment', () => {
  test('manager reassigns a task with a mandatory reason', async ({ page }) => {
    await loginAs(page, 'manager1', 'manager123');

    // Start a process first: the golden path completes the task it creates, so nothing guarantees
    // an open task is lying around for this test to reassign.
    await startDemoProcess(page);

    const firstActiveRow = await openNewestCreatedTaskRow(page);
    await expect(firstActiveRow).toBeVisible();

    await firstActiveRow.getByRole('button', { name: 'Переназначить' }).click();
    await expect(page.getByRole('button', { name: 'Подтвердить' })).toBeDisabled();

    await page.getByLabel('Роль-исполнитель').click();
    await page.getByRole('option', { name: 'MANAGER' }).click();
    await page.getByLabel('Причина').fill('Перераспределение нагрузки перед нарушением SLA');
    await expect(page.getByRole('button', { name: 'Подтвердить' })).toBeEnabled();
    await page.getByRole('button', { name: 'Подтвердить' }).click();

    await expect(page.getByRole('dialog')).not.toBeVisible();
  });
});

test.describe('role-based access', () => {
  test('analyst does not see Tasks/Templates/SLA nav items and cannot open them directly', async ({ page }) => {
    await loginAs(page, 'analyst1', 'analyst123');

    await expect(page.getByRole('button', { name: 'Процессы' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Задачи' })).not.toBeVisible();
    await expect(page.getByRole('button', { name: 'Шаблоны процессов' })).not.toBeVisible();
    await expect(page.getByRole('button', { name: 'SLA политики' })).not.toBeVisible();

    await page.goto('/templates');
    await expect(page.getByText('У вас нет прав для просмотра этой страницы.')).toBeVisible();
  });
});

test.describe('switching accounts', () => {
  test('signing in as another role does not leave the user on a forbidden page', async ({ page }) => {
    await loginAs(page, 'admin1', 'admin123');
    await page.getByRole('button', { name: 'Шаблоны процессов' }).click();
    await expect(page.getByRole('heading', { name: 'Шаблоны процессов' })).toBeVisible();

    await page.getByText('Роль: ADMIN').click();
    await page.getByRole('menuitem', { name: 'Выйти' }).click();
    await loginAs(page, 'coordinator1', 'coordinator123');

    // Lands on the register instead of the template page the previous account was on.
    await expect(page.getByRole('heading', { name: 'Реестр процессов' })).toBeVisible();
    await expect(page.getByText('У вас нет прав для просмотра этой страницы.')).not.toBeVisible();
  });

  test('a forbidden page offers a way back', async ({ page }) => {
    await loginAs(page, 'analyst1', 'analyst123');
    await page.goto('/templates');
    await expect(page.getByText('У вас нет прав для просмотра этой страницы.')).toBeVisible();

    await page.getByRole('button', { name: 'К процессам' }).click();
    await expect(page.getByRole('heading', { name: 'Реестр процессов' })).toBeVisible();
  });
});
