import { test, expect } from "@playwright/test";
import { ADMIN, cy, login, newCustomer, signedIn } from "./helpers";

// The unchanged web front end, now served by the gateway and backed by three services.

test("catalogue comes from the catalog-service through the gateway", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Find your next favourite book" })).toBeVisible();
  await expect(cy(page, "book-card")).toHaveCount(12);
  await cy(page, "search").fill("Ikigai");
  await cy(page, "search").press("Enter");
  await expect(cy(page, "book-card").first()).toContainText("Ikigai");
});

test("registers through the form (user-service)", async ({ page }) => {
  await page.goto("/#/login");
  await cy(page, "to-register").click();
  await page.locator('input[name="name"]').fill("New Reader");
  await page.locator('input[name="email"]').fill(`form-${Date.now()}@example.com`);
  await page.locator('input[name="password"]').fill("secret123");
  await cy(page, "auth-submit").click();
  await expect(page.locator("#orders-link")).toBeVisible();
});

test("cart, checkout and cancel span the order-service and the catalogue", async ({ page, request }) => {
  const { token } = await newCustomer(request);
  await signedIn(page, token);
  await page.goto("/");
  await cy(page, "search").fill("Wings of Fire");
  await cy(page, "search").press("Enter");
  await cy(page, "book-card").first().locator('[data-cy="add-to-cart"]').click();
  await expect(cy(page, "cart-count")).toHaveText("1");

  await cy(page, "cart-link").click();
  await expect(cy(page, "cart-line")).toHaveCount(1);
  await expect(cy(page, "subtotal")).toContainText("₹325");
  await cy(page, "qty-plus").click();
  await expect(cy(page, "shipping")).toHaveText("Free");

  await cy(page, "checkout").click();
  await page.locator('input[name="phone"]').fill("9876543210");
  await page.locator('input[name="address"]').fill("Flat 12, Baner Road");
  await page.locator('input[name="city"]').fill("Pune");
  await page.locator('input[name="pincode"]').fill("411045");
  await cy(page, "place-order").click();
  await expect(cy(page, "order-confirmed")).toContainText("is placed");
  await expect(cy(page, "cart-count")).toHaveText("0");

  const order = cy(page, "order").first();
  await expect(order.locator('[data-cy="status"]')).toHaveText("PLACED");
  page.once("dialog", (d) => d.accept());
  await cy(page, "cancel-order").first().click();
  await expect(cy(page, "order").first().locator('[data-cy="status"]')).toHaveText("CANCELLED");
});

test("admin dashboard combines order figures and catalogue stock", async ({ page, request }) => {
  await signedIn(page, await login(request, ADMIN.email, ADMIN.password));
  await page.goto("/#/admin");
  await expect(page.getByRole("heading", { name: "Store admin" })).toBeVisible();
  await expect(cy(page, "titles")).not.toHaveText("–");
  await expect(cy(page, "catalogue-down")).toHaveCount(0);
});
