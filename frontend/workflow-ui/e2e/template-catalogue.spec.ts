import { expect, test } from '@playwright/test';
import { loginAs } from './helpers/auth';

/**
 * The administrator's template screen: the catalogue lists every template without searching first,
 * and a draft walks create → add rule → publish, with publication blocked until the first routing
 * rule exists (TZ §2, REQ-02-002).
 */
test.describe('template catalogue', () => {
  test('lists every template with its status and rule count without searching', async ({ page }) => {
    await loginAs(page, 'admin1', 'admin123');
    await page.getByRole('button', { name: 'Шаблоны' }).click();

    // Seeded templates (scripts/seed.mjs) are on screen straight away — no search needed.
    await expect(page.getByRole('button', { name: /DEMO_EXPERTISE_REVIEW/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /DEMO_DRAFT_NO_RULES/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /DEMO_DRAFT_NO_RULES.*Черновик.*правил: 0/s })).toBeVisible();

    await page.getByRole('button', { name: 'Опубликованные' }).click();
    await expect(page.getByRole('button', { name: /DEMO_DRAFT_NO_RULES/ })).not.toBeVisible();
    await expect(page.getByRole('button', { name: /DEMO_EXPERTISE_REVIEW/ })).toBeVisible();

    await page.getByRole('button', { name: 'Все' }).click();
    await page.getByLabel('Фильтр по коду или названию').fill('MULTI');
    await expect(page.getByRole('button', { name: /DEMO_MULTI_ROUTE/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /DEMO_EXPERTISE_REVIEW/ })).not.toBeVisible();
  });

  test('a draft can only be published once it has a routing rule', async ({ page }) => {
    await loginAs(page, 'admin1', 'admin123');
    await page.getByRole('button', { name: 'Шаблоны' }).click();

    const code = `E2E_TPL_${Date.now()}`;
    await page.getByLabel('Код', { exact: true }).fill(code);
    await page.getByLabel('Название', { exact: true }).fill('E2E шаблон');
    await page.getByRole('button', { name: 'Создать' }).click();

    await expect(page.getByRole('heading', { name: `${code} v1` })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Опубликовать' })).toBeDisabled();

    await page.getByLabel('Название правила').fill('route-complex');
    // The priority field starts empty and offers the usual 10/20/30 steps.
    await page.getByLabel('Приоритет').click();
    await page.getByRole('option', { name: '10', exact: true }).click();
    await page.getByLabel('Код целевого шага').fill('EXPERT_REVIEW');
    await page.getByLabel('Целевая роль').fill('COORDINATOR');
    await page.getByLabel('Поле').fill('requestType');
    await page.getByLabel('Значение').fill('COMPLEX');
    await page.getByRole('button', { name: 'Добавить правило' }).click();

    await expect(page.getByText('[10] route-complex → EXPERT_REVIEW (COORDINATOR)')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Опубликовать' })).toBeEnabled();

    await page.getByRole('button', { name: 'Опубликовать' }).click();
    await expect(page.getByText('Опубликован').first()).toBeVisible();
    // Publication and the rule that enabled it are both in the template's journal (TZ §10).
    await expect(page.getByText('DefinitionPublished')).toBeVisible();
  });
});
