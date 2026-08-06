import { expect, test } from '@playwright/test';
import { loginAs } from './helpers/auth';

/** An administrator assembles a template: draft, routing rule, publication (REQ-02-002). */
test('a draft can only be published once it has a routing rule', async ({ page }) => {
  await loginAs(page, 'admin1', 'admin123');
  await page.getByRole('button', { name: 'Шаблоны процессов' }).click();

  const code = `E2E_TPL_${Date.now()}`;
  await page.getByLabel('Код', { exact: true }).fill(code);
  await page.getByLabel('Название', { exact: true }).fill('E2E шаблон');
  await page.getByRole('button', { name: 'Создать' }).click();

  await expect(page.getByRole('heading', { name: `${code} v1` })).toBeVisible();

  // Publishing a rule-less draft is refused by the API and explained on screen.
  await page.getByRole('button', { name: 'Опубликовать' }).click();
  await expect(page.getByText('хотя бы одно правило маршрутизации')).toBeVisible();

  await page.getByLabel('Название правила').fill('route-complex');
  await page.getByLabel('Код целевого шага').fill('EXPERT_REVIEW');
  await page.getByLabel('Целевая роль').fill('COORDINATOR');
  await page.getByLabel('Поле').fill('requestType');
  await page.getByLabel('Значение').fill('COMPLEX');
  await page.getByRole('button', { name: 'Добавить правило' }).click();
  await expect(page.getByText('[10] route-complex → EXPERT_REVIEW (COORDINATOR)')).toBeVisible();

  await page.getByRole('button', { name: 'Опубликовать' }).click();
  await expect(page.getByText('Опубликован')).toBeVisible();
});
