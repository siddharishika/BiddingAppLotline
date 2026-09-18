async function readError(response) {
  try {
    const body = await response.json();
    return body.error || "Request failed";
  } catch {
    return response.statusText || "Request failed";
  }
}

function readCookie(name) {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : "";
}

async function csrfToken() {
  const response = await fetch("/api/auth/csrf", { credentials: "include" });
  if (!response.ok) {
    return readCookie("XSRF-TOKEN");
  }
  const body = await response.json();
  return body.token || readCookie("XSRF-TOKEN");
}

export async function api(path, options = {}) {
  const { timeoutMs, headers: extraHeaders, ...fetchOptions } = options;
  const headers = { ...(extraHeaders || {}) };
  const method = (fetchOptions.method || "GET").toUpperCase();
  if (fetchOptions.body && !headers["Content-Type"]) {
    headers["Content-Type"] = "application/json";
  }
  if (method !== "GET" && method !== "HEAD") {
    headers["X-XSRF-TOKEN"] = await csrfToken();
  }

  const controller = timeoutMs ? new AbortController() : null;
  const timer = timeoutMs ? setTimeout(() => controller.abort(), timeoutMs) : undefined;
  let response;
  try {
    response = await fetch(path, {
      credentials: "include",
      ...fetchOptions,
      headers,
      signal: fetchOptions.signal ?? controller?.signal,
    });
  } catch (err) {
    if (err?.name === "AbortError") {
      throw new Error("Stripe did not respond in time. No payment was recorded.");
    }
    throw err;
  } finally {
    if (timer) {
      clearTimeout(timer);
    }
  }

  if (response.status === 204) {
    return null;
  }
  if (!response.ok) {
    throw new Error(await readError(response));
  }
  if (response.headers.get("content-type")?.includes("application/json")) {
    return response.json();
  }
  return null;
}

export const getMe = () => api("/api/auth/me");
export const login = (username, password) =>
  api("/api/auth/login", { method: "POST", body: JSON.stringify({ username, password }) });
export const register = (payload) =>
  api("/api/auth/register", { method: "POST", body: JSON.stringify(payload) });
export const logout = () => api("/api/auth/logout", { method: "POST" });
export const listAuctions = (status) =>
  api(status ? `/api/auctions?status=${encodeURIComponent(status)}` : "/api/auctions");
export const getAuction = (id) => api(`/api/auctions/${id}`);
export const listCollections = (status) =>
  api(status ? `/api/collections?status=${encodeURIComponent(status)}` : "/api/collections");
export const getCollection = (id) => api(`/api/collections/${id}`);
export const createAuction = (payload) =>
  api("/api/auctions", { method: "POST", body: JSON.stringify(payload) });
export const createCollection = (payload) =>
  api("/api/auctions/collections", { method: "POST", body: JSON.stringify(payload) });
export const placeBid = (id, amount) =>
  api(`/api/auctions/${id}/bids`, { method: "POST", body: JSON.stringify({ amount }) });
export const getPaymentGateway = () => api("/api/account/payments/gateway");
export const startCheckout = (id) =>
  api(`/api/auctions/${id}/checkout`, { method: "POST", timeoutMs: 25000 });
export const completeCheckout = (sessionId) =>
  api("/api/account/payments/checkout/complete", {
    method: "POST",
    body: JSON.stringify({ sessionId }),
    timeoutMs: 25000,
  });
export const getCategories = () => api("/api/categories");
export const getMyLots = () => api("/api/account/lots");
export const getMyBids = () => api("/api/account/bids");
export const getPayments = () => api("/api/account/payments", { timeoutMs: 30000 });
export const withdrawLot = (id) => api(`/api/account/lots/${id}/withdraw`, { method: "POST" });
export const reopenLot = (id) => api(`/api/account/lots/${id}/reopen`, { method: "POST" });
export const deleteLot = (id) => api(`/api/account/lots/${id}`, { method: "DELETE" });
export const withdrawCollection = (id) => api(`/api/account/collections/${id}/withdraw`, { method: "POST" });
export const reopenCollection = (id) => api(`/api/account/collections/${id}/reopen`, { method: "POST" });
export const deleteCollection = (id) => api(`/api/account/collections/${id}`, { method: "DELETE" });
