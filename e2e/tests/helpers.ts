import { expect, type APIRequestContext, type Page } from "@playwright/test";

export const ADMIN = { email: "admin@buchhaus.local", password: "Admin@12345" };

export async function login(request: APIRequestContext, email: string, password: string): Promise<string> {
  const res = await request.post("/api/auth/login", { data: { email, password } });
  expect(res.status()).toBe(200);
  return (await res.json()).token;
}

export async function newCustomer(request: APIRequestContext): Promise<{ email: string; token: string }> {
  const email = `pw-${Date.now()}-${Math.random().toString(36).slice(2, 7)}@example.com`;
  const res = await request.post("/api/auth/register", { data: { name: "Playwright Reader", email, password: "secret123" } });
  expect(res.status()).toBe(201);
  return { email, token: (await res.json()).token };
}

/** Opens the app already signed in (the front end keeps the JWT in localStorage). */
export async function signedIn(page: Page, token: string) {
  await page.addInitScript((t) => window.localStorage.setItem("buchhaus.token", t), token);
}

export const cy = (page: Page, name: string) => page.locator(`[data-cy="${name}"]`);

export const auth = (token: string) => ({ Authorization: `Bearer ${token}` });
