import type { Page } from '@playwright/test';

/** Drives Keycloak's hosted login form after the app redirects there for an unauthenticated visit. */
export async function loginAs(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/');
  await page.waitForURL(/\/realms\/workflow\/protocol\/openid-connect\/auth/);
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#kc-login').click();
  await page.waitForURL((url) => !url.pathname.includes('/realms/'));
}
