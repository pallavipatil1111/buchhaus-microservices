import { test, expect } from "@playwright/test";
import { ADMIN, auth, login, newCustomer } from "./helpers";

// Security and routing at the edge. These talk to the API directly.

test("signed-in routes need a valid token", async ({ request }) => {
  const res = await request.get("/api/cart");
  expect(res.status()).toBe(401);
  expect((await res.json()).message).toBe("Please sign in");
  expect((await request.get("/api/cart", { headers: { Authorization: "Bearer not.a.token" } })).status()).toBe(401);
});

test("customers cannot reach admin routes, even by forging identity headers", async ({ request }) => {
  const { token } = await newCustomer(request);
  expect((await request.get("/api/admin/stats", { headers: auth(token) })).status()).toBe(403);
  // the gateway strips client-sent identity headers before forwarding
  const forged = await request.get("/api/admin/stats", { headers: { ...auth(token), "X-User-Role": "ADMIN" } });
  expect(forged.status()).toBe(403);
  const noToken = await request.get("/api/cart", { headers: { "X-User-Id": "1" } });
  expect(noToken.status()).toBe(401);
});

test("the services' internal API and tricky paths are not reachable from outside", async ({ request }) => {
  expect((await request.get("/internal/summary")).status()).toBe(404);
  expect((await request.post("/internal/reservations", { data: {} })).status()).toBe(404);
  const admin = await login(request, ADMIN.email, ADMIN.password);
  expect((await request.get("/api/books/..%2Fadmin/stats", { headers: auth(admin) })).status()).toBe(400);
});

test("the last copy is sold once even when two customers check out together", async ({ request }) => {
  const admin = await login(request, ADMIN.email, ADMIN.password);
  const sku = `PW-${Date.now() % 1_000_000}`;
  const created = await request.post("/api/admin/books", {
    headers: auth(admin),
    data: { sku, title: `Last Copy ${sku}`, author: "Tester", genre: "Testing", price: "300.00", stock: 1 },
  });
  expect(created.status()).toBe(201);
  const bookId = (await created.json()).id;

  const a = await newCustomer(request);
  const b = await newCustomer(request);
  for (const c of [a, b]) {
    const put = await request.put(`/api/cart/items/${bookId}`, { headers: auth(c.token), data: { quantity: 1 } });
    expect(put.status()).toBe(200);
  }
  const address = { name: "Reader", phone: "9876543210", address: "Flat 1", city: "Pune", pincode: "411045" };
  const results = await Promise.all([a, b].map((c) => request.post("/api/orders", { headers: auth(c.token), data: address })));
  const statuses = results.map((r) => r.status()).sort();
  expect(statuses).toEqual([201, 409]);
  const loser = results.find((r) => r.status() === 409)!;
  expect((await loser.json()).message).toContain("sold out");
});
