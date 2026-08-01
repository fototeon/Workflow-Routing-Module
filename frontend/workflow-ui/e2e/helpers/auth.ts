import { expect, type Page } from '@playwright/test';

/** Signs in through the application's own login form, which exchanges the credentials for a token at Keycloak. */
export async function loginAs(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/');
  await page.getByLabel('Логин').fill(username);
  await page.getByLabel('Пароль').fill(password);
  await page.getByRole('button', { name: 'Войти' }).click();
  await expect(page.getByRole('button', { name: 'Процессы' })).toBeVisible();
}
