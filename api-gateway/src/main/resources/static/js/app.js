// BuchHaus storefront + admin - a small hash-routed single-page app (no framework, CSP-friendly).
import { api, setToken, getToken, onUnauthorized } from "./api.js";

const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const view = () => $("#view");

/** DOM builder: text always goes in as textContent, never as HTML. */
function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v === null || v === undefined || v === false) continue;
    if (k === "class") el.className = v;
    else if (k.startsWith("on")) el.addEventListener(k.slice(2), v);
    else if (v === true) el.setAttribute(k, "");
    else el.setAttribute(k, v);
  }
  for (const c of children.flat()) {
    if (c === null || c === undefined || c === false) continue;
    el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return el;
}

const inr = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", minimumFractionDigits: 0, maximumFractionDigits: 2 });
const money = (v) => inr.format(Number(v));
const state = { me: null, cart: null, genres: [], query: { q: "", genre: "", sort: "relevance", page: 0 } };

function toast(msg, kind = "ok") {
  const t = h("div", { class: `toast ${kind === "error" ? "error" : ""}`, role: kind === "error" ? "alert" : "status", "data-cy": "toast" }, msg);
  $("#toasts").append(t);
  setTimeout(() => t.remove(), 3200);
}

/** A generated "cover": colour picked from the title, so every book looks different but stable. */
function cover(book, big = false) {
  let hash = 0;
  for (const ch of book.title) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
  return h("div", { class: `cover c${hash % 8}${big ? " big" : ""}`, "aria-hidden": "true" },
    h("span", { class: "ct" }, book.title), h("span", { class: "ca" }, book.author));
}

function stockLabel(b) {
  if (b.stock === 0) return h("span", { class: "stock out" }, "Out of stock");
  if (b.stock <= 5) return h("span", { class: "stock low" }, `Only ${b.stock} left`);
  return h("span", { class: "stock muted" }, "In stock");
}

function showErrors(form, err) {
  $$("[aria-invalid]", form).forEach((e) => e.removeAttribute("aria-invalid"));
  for (const f of Object.keys(err.fields ?? {})) form.elements[f]?.setAttribute("aria-invalid", "true");
  const detail = Object.entries(err.fields ?? {}).map(([k, v]) => `${k} ${v}`).join("; ");
  $(".form-error", form).textContent = detail ? `${err.message}: ${detail}` : err.message;
}

// ---------- session ----------
onUnauthorized(() => { setToken(null); state.me = null; renderNav(); location.hash = "#/login"; });

async function loadMe() {
  if (!getToken()) { state.me = null; return; }
  try { state.me = await api("/api/me"); } catch { state.me = null; setToken(null); }
}

function renderNav() {
  const me = state.me;
  $("#login-link").hidden = !!me;
  $("#logout").hidden = !me;
  $("#orders-link").hidden = !me;
  $("#admin-link").hidden = me?.role !== "ADMIN";
  $("#cart-count").textContent = state.cart?.itemCount ?? 0;
}

async function refreshCart() {
  state.cart = state.me ? await api("/api/cart") : null;
  renderNav();
}

function requireLogin() {
  if (state.me) return true;
  sessionStorage.setItem("afterLogin", location.hash);
  location.hash = "#/login";
  return false;
}

async function addToCart(book, quantity = 1) {
  if (!requireLogin()) return;
  const current = state.cart?.items.find((l) => l.book.id === book.id)?.quantity ?? 0;
  try {
    state.cart = await api(`/api/cart/items/${book.id}`, { method: "PUT", body: { quantity: current + quantity } });
    renderNav();
    toast(`Added “${book.title}” to your cart`);
  } catch (err) {
    toast(err.message, "error");
  }
}

// ---------- catalogue ----------
async function catalogPage() {
  const { q, genre, sort, page } = state.query;
  const params = new URLSearchParams({ sort, page, size: 12 });
  if (q) params.set("q", q);
  if (genre) params.set("genre", genre);
  const [result] = await Promise.all([api(`/api/books?${params}`, { auth: false }),
    state.genres.length ? null : api("/api/genres", { auth: false }).then((g) => { state.genres = g; })]);

  const chips = h("div", { class: "chips", role: "group", "aria-label": "Genres" },
    ...["", ...state.genres].map((g) => h("button", {
      class: "chip", type: "button", "aria-pressed": String(genre === g), "data-cy": "genre",
      onclick: () => { state.query.genre = g; state.query.page = 0; render(); },
    }, g || "All")));
  const sortSel = h("select", { "aria-label": "Sort", "data-cy": "sort", onchange: (e) => { state.query.sort = e.target.value; state.query.page = 0; render(); } },
    ...[["relevance", "Title A–Z"], ["price_asc", "Price: low to high"], ["price_desc", "Price: high to low"], ["newest", "Newest"]]
      .map(([v, l]) => h("option", { value: v, selected: v === sort }, l)));

  const grid = h("section", { class: "books", "aria-label": "Books" },
    ...(result.content.length ? result.content.map(bookCard) : [h("p", { class: "empty" }, q ? `No books match “${q}”.` : "No books here yet.")]));
  const pager = result.totalPages > 1 ? h("nav", { class: "pager", "aria-label": "Pages" },
    h("button", { class: "btn ghost small", type: "button", disabled: page === 0, onclick: () => { state.query.page--; render(); } }, "← Previous"),
    h("span", { class: "muted", "data-cy": "page" }, `Page ${page + 1} of ${result.totalPages}`),
    h("button", { class: "btn ghost small", type: "button", disabled: page + 1 >= result.totalPages, onclick: () => { state.query.page++; render(); } }, "Next →")) : "";

  view().replaceChildren(
    q ? h("h1", {}, `Results for “${q}”`) : h("section", { class: "hero" },
      h("div", {}, h("h1", {}, "Find your next favourite book"), h("p", {}, "Indian writing, timeless classics and the best of technology - delivered across India.")),
      h("span", { class: "perk" }, "Free delivery from ₹499")),
    h("div", { class: "filters" }, chips, sortSel),
    h("p", { class: "muted", "data-cy": "result-count" }, `${result.totalElements} book${result.totalElements === 1 ? "" : "s"}`),
    grid, pager);
}

function bookCard(b) {
  return h("article", { class: "book", "data-cy": "book-card", "data-id": b.id },
    h("a", { href: `#/book/${b.id}`, "aria-label": b.title }, cover(b)),
    h("div", { class: "meta" }, h("a", { href: `#/book/${b.id}` }, h("h3", {}, b.title)), h("div", { class: "author" }, b.author)),
    h("div", { class: "price-row" }, h("span", { class: "price" }, money(b.price)), stockLabel(b)),
    h("button", { class: "btn primary small", type: "button", disabled: b.stock === 0, "data-cy": "add-to-cart", onclick: () => addToCart(b) },
      b.stock === 0 ? "Sold out" : "Add to cart"));
}

async function bookPage(id) {
  let b;
  try { b = await api(`/api/books/${id}`, { auth: false }); } catch { view().replaceChildren(h("p", { class: "empty" }, "That book isn't available.")); return; }
  let qty = 1;
  const qtyLabel = h("span", {}, "1");
  const setQty = (n) => { qty = Math.max(1, Math.min(n, b.stock, 10)); qtyLabel.textContent = qty; };
  view().replaceChildren(h("article", { class: "detail" }, cover(b, true),
    h("div", {},
      h("p", { class: "muted" }, h("a", { href: "#/" }, "Books"), ` / ${b.genre}`),
      h("h1", { "data-cy": "book-title" }, b.title), h("p", { class: "by" }, `by ${b.author}`),
      b.description ? h("p", {}, b.description) : "",
      h("div", { class: "facts" }, b.publishedYear ? h("span", {}, `Published ${b.publishedYear}`) : "", b.pages ? h("span", {}, `${b.pages} pages`) : "", h("span", {}, `SKU ${b.sku}`)),
      h("div", { class: "price-row" }, h("span", { class: "price", "data-cy": "book-price" }, money(b.price)), stockLabel(b)),
      h("div", { class: "buy" },
        h("div", { class: "qty" }, h("button", { type: "button", "aria-label": "Fewer", onclick: () => setQty(qty - 1) }, "−"), qtyLabel,
          h("button", { type: "button", "aria-label": "More", onclick: () => setQty(qty + 1) }, "+")),
        h("button", { class: "btn accent", type: "button", disabled: b.stock === 0, "data-cy": "add-to-cart", onclick: () => addToCart(b, qty) }, b.stock === 0 ? "Sold out" : "Add to cart")))));
}

// ---------- cart & checkout ----------
async function cartPage() {
  if (!requireLogin()) return;
  await refreshCart();
  const c = state.cart;
  if (!c.items.length) {
    view().replaceChildren(h("h1", {}, "Your cart"), h("p", { class: "empty", "data-cy": "empty-cart" }, "Your cart is empty. ", h("a", { href: "#/" }, "Browse books")));
    return;
  }
  const lines = c.items.map((l) => h("div", { class: "line", "data-cy": "cart-line" },
    cover(l.book),
    h("div", {}, h("a", { href: `#/book/${l.book.id}` }, h("strong", {}, l.book.title)), h("div", { class: "muted" }, money(l.book.price)),
      l.inStock ? "" : h("div", { class: "warn" }, l.book.stock === 0 ? "Sold out - please remove" : `Only ${l.book.stock} left - reduce quantity`)),
    h("div", { class: "qty" },
      h("button", { type: "button", "aria-label": `Fewer ${l.book.title}`, "data-cy": "qty-minus", onclick: () => updateQty(l.book.id, l.quantity - 1) }, "−"),
      h("span", { "data-cy": "qty" }, l.quantity),
      h("button", { type: "button", "aria-label": `More ${l.book.title}`, "data-cy": "qty-plus", onclick: () => updateQty(l.book.id, l.quantity + 1) }, "+")),
    h("strong", {}, money(l.lineTotal))));
  view().replaceChildren(h("h1", {}, "Your cart"), h("div", { class: "cart-layout" },
    h("section", { class: "card" }, ...lines),
    summary(c, h("a", { class: `btn accent${c.canCheckout ? "" : " disabled"}`, href: c.canCheckout ? "#/checkout" : null, "aria-disabled": String(!c.canCheckout), "data-cy": "checkout" }, "Proceed to checkout"))));
}

function summary(c, action) {
  const pct = Math.min(100, (Number(c.subtotal) / 499) * 100);
  const bar = h("span");
  requestAnimationFrame(() => { bar.style.width = `${pct}%`; });
  return h("aside", { class: "card summary" },
    h("h2", {}, "Summary"),
    h("div", { class: "row" }, h("span", {}, `Items (${c.itemCount})`), h("span", { "data-cy": "subtotal" }, money(c.subtotal))),
    h("div", { class: "row" }, h("span", {}, "Delivery"), h("span", { "data-cy": "shipping" }, Number(c.shipping) === 0 ? "Free" : money(c.shipping))),
    Number(c.freeShippingGap) > 0 ? h("div", {}, h("div", { class: "progress" }, bar), h("p", { class: "note" }, `Add ${money(c.freeShippingGap)} more for free delivery`)) : h("p", { class: "note" }, "You get free delivery 🎉"),
    h("div", { class: "row total" }, h("span", {}, "Total"), h("span", { "data-cy": "total" }, money(c.total))),
    action);
}

async function updateQty(bookId, quantity) {
  try {
    state.cart = await api(`/api/cart/items/${bookId}`, { method: "PUT", body: { quantity: Math.max(0, quantity) } });
    await cartPage();
  } catch (err) {
    toast(err.message, "error");
  }
}

async function checkoutPage() {
  if (!requireLogin()) return;
  await refreshCart();
  if (!state.cart.items.length) { location.hash = "#/cart"; return; }
  const form = h("form", { class: "card stack", novalidate: true, "data-cy": "checkout-form" },
    h("h2", {}, "Delivery address"),
    h("label", {}, "Full name", h("input", { name: "name", required: true, value: state.me.name, autocomplete: "name" })),
    h("label", {}, "Mobile number", h("input", { name: "phone", required: true, inputmode: "numeric", maxlength: "10", placeholder: "9876543210", autocomplete: "tel-national" })),
    h("label", {}, "Address", h("input", { name: "address", required: true, placeholder: "Flat, building, street", autocomplete: "street-address" })),
    h("div", { class: "grid2" },
      h("label", {}, "City", h("input", { name: "city", required: true, autocomplete: "address-level2" })),
      h("label", {}, "PIN code", h("input", { name: "pincode", required: true, inputmode: "numeric", maxlength: "6", autocomplete: "postal-code" }))),
    h("p", { class: "note" }, "Pay on delivery by cash or UPI."),
    h("p", { class: "form-error", role: "alert" }),
    h("button", { class: "btn accent", type: "submit", "data-cy": "place-order" }, `Place order · ${money(state.cart.total)}`));
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const body = Object.fromEntries(["name", "phone", "address", "city", "pincode"].map((k) => [k, form.elements[k].value.trim()]));
    const button = $("button[type=submit]", form);
    button.disabled = true;
    try {
      const order = await api("/api/orders", { method: "POST", body });
      await refreshCart();
      location.hash = `#/orders/${order.id}`;
    } catch (err) {
      showErrors(form, err);
      button.disabled = false;
    }
  });
  view().replaceChildren(h("h1", {}, "Checkout"), h("div", { class: "cart-layout" }, form, summary(state.cart, "")));
}

// ---------- orders ----------
function orderCard(o, { admin = false, onChange } = {}) {
  const actions = [];
  if (!admin && o.canCancel) {
    actions.push(h("button", { class: "btn danger small", type: "button", "data-cy": "cancel-order", onclick: async () => {
      if (!confirm("Cancel this order?")) return;
      try { await api(`/api/orders/${o.id}/cancel`, { method: "POST" }); toast("Order cancelled"); onChange?.(); } catch (err) { toast(err.message, "error"); }
    } }, "Cancel order"));
  }
  if (admin && o.nextStatuses.length) {
    const sel = h("select", { "aria-label": `Status for order ${o.id}`, "data-cy": "order-status" },
      h("option", { value: "" }, "Move to…"), ...o.nextStatuses.map((s) => h("option", { value: s }, s)));
    sel.addEventListener("change", async () => {
      if (!sel.value) return;
      try { await api(`/api/admin/orders/${o.id}/status`, { method: "PATCH", body: { status: sel.value } }); toast(`Order #${o.id} is now ${sel.value}`); onChange?.(); } catch (err) { toast(err.message, "error"); }
    });
    actions.push(sel);
  }
  return h("article", { class: "card", "data-cy": "order", "data-id": o.id },
    h("div", { class: "order-head" },
      h("div", {}, h("strong", {}, `Order #${o.id}`), " ", h("span", { class: `status ${o.status}`, "data-cy": "status" }, o.status),
        h("div", { class: "muted" }, new Date(o.createdAt).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" }), admin ? ` · ${o.customerEmail}` : "")),
      h("div", {}, ...actions)),
    h("ul", { class: "order-items" }, ...o.items.map((i) => h("li", {}, h("span", {}, `${i.title} × ${i.quantity}`), h("span", {}, money(i.lineTotal))))),
    h("div", { class: "order-head" }, h("span", { class: "muted" }, `${o.shipTo.name}, ${o.shipTo.city} ${o.shipTo.pincode}`),
      h("strong", {}, `Total ${money(o.total)}`, Number(o.shipping) ? h("span", { class: "muted" }, ` (incl. ${money(o.shipping)} delivery)`) : "")));
}

async function ordersPage(focusId) {
  if (!requireLogin()) return;
  const orders = await api("/api/orders");
  view().replaceChildren(
    focusId ? h("div", { class: "banner", "data-cy": "order-confirmed" }, `Thank you! Order #${focusId} is placed. We'll text you when it ships.`) : "",
    h("h1", {}, "Your orders"),
    h("section", { class: "orders" }, ...(orders.length ? orders.map((o) => orderCard(o, { onChange: () => ordersPage() })) : [h("p", { class: "empty" }, "No orders yet.")])));
}

// ---------- auth ----------
function authPage(mode) {
  const register = mode === "register";
  const form = h("form", { class: "card stack auth", novalidate: true },
    h("h1", {}, register ? "Create your account" : "Sign in"),
    register ? h("label", {}, "Name", h("input", { name: "name", autocomplete: "name", required: true })) : "",
    h("label", {}, "Email", h("input", { name: "email", type: "email", autocomplete: register ? "email" : "username", required: true })),
    h("label", {}, "Password", h("input", { name: "password", type: "password", autocomplete: register ? "new-password" : "current-password", required: true })),
    h("p", { class: "form-error", role: "alert" }),
    h("button", { class: "btn primary", type: "submit", "data-cy": "auth-submit" }, register ? "Create account" : "Sign in"),
    register ? h("p", { class: "muted" }, "Already have an account? ", h("a", { href: "#/login" }, "Sign in"))
      : h("p", { class: "muted" }, "New to BuchHaus? ", h("a", { href: "#/register", "data-cy": "to-register" }, "Create an account"), h("br"), "Demo: reader@buchhaus.local / reader1234"));
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const body = { email: form.elements.email.value.trim(), password: form.elements.password.value };
    if (register) body.name = form.elements.name.value.trim();
    try {
      const res = await api(register ? "/api/auth/register" : "/api/auth/login", { method: "POST", body, auth: false });
      setToken(res.token);
      state.me = res.user;
      await refreshCart();
      const back = sessionStorage.getItem("afterLogin");
      sessionStorage.removeItem("afterLogin");
      location.hash = back && !back.startsWith("#/login") ? back : (res.user.role === "ADMIN" ? "#/admin" : "#/");
    } catch (err) {
      showErrors(form, err);
    }
  });
  view().replaceChildren(form);
}

// ---------- admin ----------
let adminTab = "books";
async function adminPage() {
  if (!requireLogin()) return;
  if (state.me.role !== "ADMIN") { view().replaceChildren(h("p", { class: "empty" }, "Admins only.")); return; }
  const stats = await api("/api/admin/stats");
  const tile = (k, v, cy) => h("div", { class: "tile" }, h("div", { class: "k" }, k), h("div", { class: "v", "data-cy": cy }, v));
  const tabs = h("div", { class: "tabs", role: "tablist" }, ...[["books", "Books"], ["orders", "Orders"]].map(([k, l]) =>
    h("button", { class: "tab", role: "tab", "aria-selected": String(adminTab === k), "data-cy": `tab-${k}`, type: "button", onclick: () => { adminTab = k; adminPage(); } }, l)));
  const body = h("div");
  view().replaceChildren(h("h1", {}, "Store admin"),
    h("div", { class: "tiles" }, tile("Revenue", money(stats.revenue), "revenue"), tile("Orders", stats.orders, "order-count"),
      tile("To ship", stats.ordersByStatus.PLACED ?? 0, "to-ship"), tile("Titles", stats.activeTitles ?? "–", "titles")),
    stats.catalogueAvailable === false ? h("p", { class: "notice", role: "status", "data-cy": "catalogue-down" }, "The catalogue service is not responding, so stock figures are hidden. Orders still work as normal and the figures return automatically.") : "",
    h("div", { class: "split" },
      h("section", { class: "card" }, h("h2", {}, "Low stock"), h("ul", { class: "simple-list" }, ...(stats.lowStock.length ? stats.lowStock.map((b) => h("li", {}, h("span", {}, b.title), h("strong", {}, `${b.stock} left`))) : [h("li", { class: "muted" }, stats.catalogueAvailable === false ? "Unavailable right now" : "All titles well stocked")]))),
      h("section", { class: "card" }, h("h2", {}, "Top sellers"), h("ul", { class: "simple-list" }, ...(stats.topSellers.length ? stats.topSellers.map((t) => h("li", {}, h("span", {}, t.title), h("strong", {}, `${t.copies} sold`))) : [h("li", { class: "muted" }, "No sales yet")])))),
    tabs, body);
  try {
    if (adminTab === "books") await adminBooks(body); else await adminOrders(body);
  } catch (err) {
    // One service being down should not blank the whole dashboard.
    body.replaceChildren(h("p", { class: "notice", role: "status" }, err.message));
  }
}

async function adminBooks(root) {
  const books = await api("/api/admin/books");
  $("#genre-list").replaceChildren(...[...new Set(books.map((b) => b.genre))].map((g) => h("option", { value: g })));
  root.replaceChildren(
    h("div", { class: "order-head" }, h("p", { class: "muted" }, `${books.length} titles`), h("button", { class: "btn primary", type: "button", "data-cy": "add-book", onclick: () => openBookDialog() }, "+ Add book")),
    h("div", { class: "table-wrap" }, h("table", {},
      h("thead", {}, h("tr", {}, ...["SKU", "Title", "Author", "Genre", "Price", "Stock", ""].map((t) => h("th", {}, t)))),
      h("tbody", {}, ...books.map((b) => h("tr", { class: b.active ? "" : "retired", "data-cy": "book-row" },
        h("td", {}, b.sku), h("td", {}, b.title, b.active ? "" : h("span", { class: "muted" }, " (retired)")), h("td", {}, b.author), h("td", {}, b.genre),
        h("td", {}, money(b.price)), h("td", {}, b.stock),
        h("td", {}, h("button", { class: "btn ghost small", type: "button", "data-cy": "edit-book", onclick: () => openBookDialog(b) }, "Edit"), " ",
          b.active ? h("button", { class: "btn danger small", type: "button", "data-cy": "remove-book", onclick: () => removeBook(b) }, "Remove") : "")))))));
}

let editing = null;
function openBookDialog(b = null) {
  editing = b;
  const form = $("#book-form");
  form.reset();
  $(".form-error", form).textContent = "";
  $$("[aria-invalid]", form).forEach((e) => e.removeAttribute("aria-invalid"));
  $("#book-dialog-title").textContent = b ? `Edit “${b.title}”` : "Add a book";
  if (b) for (const k of ["sku", "title", "author", "genre", "price", "stock", "publishedYear", "pages", "description"]) form.elements[k].value = b[k] ?? "";
  $("#book-dialog").showModal();
}

async function saveBook(e) {
  e.preventDefault();
  const form = $("#book-form");
  const v = (k) => form.elements[k].value.trim();
  const num = (k) => (v(k) === "" ? null : Number(v(k)));
  const body = { sku: v("sku").toUpperCase(), title: v("title"), author: v("author"), genre: v("genre"), price: v("price") || null,
    stock: num("stock"), publishedYear: num("publishedYear"), pages: num("pages"), description: v("description") || null,
    version: editing?.version ?? null };
  try {
    await api(editing ? `/api/admin/books/${editing.id}` : "/api/admin/books", { method: editing ? "PUT" : "POST", body });
    $("#book-dialog").close();
    toast(editing ? "Book updated" : "Book added");
    await adminPage();
  } catch (err) {
    showErrors(form, err);
  }
}

async function removeBook(b) {
  if (!confirm(`Remove “${b.title}”? Books with past orders are retired instead of deleted.`)) return;
  try {
    const r = await api(`/api/admin/books/${b.id}`, { method: "DELETE" });
    toast(r.result === "deleted" ? "Book deleted" : "Book retired (it has past orders)");
    await adminPage();
  } catch (err) {
    toast(err.message, "error");
  }
}

let orderFilter = "";
async function adminOrders(root) {
  const orders = await api(`/api/admin/orders${orderFilter ? `?status=${orderFilter}` : ""}`);
  const filter = h("select", { "aria-label": "Filter by status", "data-cy": "order-filter", onchange: (e) => { orderFilter = e.target.value; adminPage(); } },
    ...["", "PLACED", "SHIPPED", "DELIVERED", "CANCELLED"].map((s) => h("option", { value: s, selected: s === orderFilter }, s || "All statuses")));
  root.replaceChildren(h("div", { class: "filters" }, filter),
    h("section", { class: "orders" }, ...(orders.length ? orders.map((o) => orderCard(o, { admin: true, onChange: () => adminPage() })) : [h("p", { class: "empty" }, "No orders.")])));
}

// ---------- router ----------
async function render() {
  const [, path = "", arg] = location.hash.replace(/^#/, "").split("/");
  try {
    if (path === "") await catalogPage();
    else if (path === "book") await bookPage(arg);
    else if (path === "cart") await cartPage();
    else if (path === "checkout") await checkoutPage();
    else if (path === "orders") await ordersPage(arg);
    else if (path === "login") authPage("login");
    else if (path === "register") authPage("register");
    else if (path === "admin") await adminPage();
    else view().replaceChildren(h("p", { class: "empty" }, "Page not found."));
  } catch (err) {
    if (err.status !== 401) toast(err.message, "error");
  }
  window.scrollTo(0, 0);
}

$("#search-form").addEventListener("submit", (e) => {
  e.preventDefault();
  state.query = { ...state.query, q: $("#search").value.trim(), page: 0 };
  if (location.hash !== "#/" && location.hash !== "") location.hash = "#/"; else render();
});
$("#logout").addEventListener("click", () => { setToken(null); state.me = null; state.cart = null; renderNav(); location.hash = "#/"; toast("Signed out"); });
$("#book-form").addEventListener("submit", saveBook);
$$("[data-close]").forEach((b) => b.addEventListener("click", () => b.closest("dialog").close()));
window.addEventListener("hashchange", render);

await loadMe();
await refreshCart().catch(() => {});
renderNav();
await render();
