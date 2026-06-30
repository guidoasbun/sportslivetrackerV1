/**
 * Token Manager — client-side fetch wrapper with automatic token refresh.
 *
 * - Intercepts 401 responses and coordinates a single refresh call.
 * - Proactively refreshes the access token when < 5 minutes remain on the JWT `exp` claim.
 * - Queues concurrent requests during refresh so only one refresh call is made at a time.
 * - On refresh failure: retries once after 2 seconds, then clears session and redirects to /login.
 */

// ---------------------------------------------------------------------------
// JWT decoding helpers (lightweight, no external library needed)
// ---------------------------------------------------------------------------

/**
 * Decodes a base64url-encoded string to a regular string.
 */
function base64UrlDecode(input: string): string {
  // Replace base64url chars with standard base64 chars
  let base64 = input.replace(/-/g, "+").replace(/_/g, "/");
  // Pad with '=' to make length a multiple of 4
  const pad = base64.length % 4;
  if (pad) {
    base64 += "=".repeat(4 - pad);
  }
  return atob(base64);
}

/**
 * Extracts the `exp` claim (expiration time in seconds since epoch) from a JWT.
 * Returns `null` if the token cannot be decoded or does not contain an `exp` claim.
 */
function getTokenExp(token: string): number | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const payload = JSON.parse(base64UrlDecode(parts[1]));
    return typeof payload.exp === "number" ? payload.exp : null;
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------------------
// Cookie helpers
// ---------------------------------------------------------------------------

/**
 * Reads the value of a cookie by name from `document.cookie`.
 */
function getCookie(name: string): string | undefined {
  const cookies = document.cookie.split(";");
  for (const cookie of cookies) {
    const [key, ...rest] = cookie.split("=");
    if (key.trim() === name) {
      return decodeURIComponent(rest.join("="));
    }
  }
  return undefined;
}

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** Threshold in seconds for proactive refresh (5 minutes). */
const PROACTIVE_REFRESH_THRESHOLD_SECONDS = 5 * 60;

/** Delay before retrying a failed refresh (2 seconds). */
const REFRESH_RETRY_DELAY_MS = 2000;

// ---------------------------------------------------------------------------
// Core implementation
// ---------------------------------------------------------------------------

/**
 * Creates a fetch-compatible function that transparently handles token refresh.
 *
 * Usage:
 * ```ts
 * const authFetch = createAuthFetch();
 * const response = await authFetch('/api/some-endpoint');
 * ```
 */
export function createAuthFetch(): typeof fetch {
  /**
   * Shared refresh promise — ensures only one refresh request is in-flight at a time.
   * Concurrent 401 responses or proactive refresh checks will await this same promise.
   */
  let refreshPromise: Promise<boolean> | null = null;

  /**
   * Attempts to refresh the access token by calling the server-side refresh endpoint.
   * Returns `true` if the refresh succeeded, `false` otherwise.
   */
  async function doRefresh(): Promise<boolean> {
    const response = await fetch("/api/auth/refresh", {
      method: "POST",
      credentials: "include",
    });
    return response.ok;
  }

  /**
   * Coordinates a single refresh attempt with one retry on failure.
   * On final failure, clears session and redirects to /login.
   * Returns `true` if refresh ultimately succeeds.
   */
  async function refreshWithRetry(): Promise<boolean> {
    // First attempt
    const firstAttempt = await doRefresh().catch(() => false);
    if (firstAttempt) return true;

    // Wait 2 seconds before retrying
    await new Promise((resolve) => setTimeout(resolve, REFRESH_RETRY_DELAY_MS));

    // Second attempt
    const secondAttempt = await doRefresh().catch(() => false);
    if (secondAttempt) return true;

    // Both attempts failed — clear session and redirect
    clearSessionAndRedirect();
    return false;
  }

  /**
   * Ensures only a single refresh flow runs at a time.
   * All callers await the same promise.
   */
  function triggerRefresh(): Promise<boolean> {
    if (!refreshPromise) {
      refreshPromise = refreshWithRetry().finally(() => {
        refreshPromise = null;
      });
    }
    return refreshPromise;
  }

  /**
   * Clears the session cookie and redirects to the login page.
   */
  function clearSessionAndRedirect(): void {
    // Clear the session cookie from the client side
    document.cookie = "session=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
    window.location.href = "/login";
  }

  /**
   * Checks the access token's expiration and triggers a proactive refresh
   * if fewer than 5 minutes remain.
   */
  function maybeProactiveRefresh(): void {
    const token = getCookie("session");
    if (!token) return;

    const exp = getTokenExp(token);
    if (exp === null) return;

    const nowSeconds = Math.floor(Date.now() / 1000);
    const remaining = exp - nowSeconds;

    if (remaining < PROACTIVE_REFRESH_THRESHOLD_SECONDS) {
      // Fire and forget — proactive refresh runs in the background
      triggerRefresh();
    }
  }

  /**
   * The wrapped fetch function. Drop-in replacement for native `fetch`.
   */
  async function authFetch(
    input: RequestInfo | URL,
    init?: RequestInit
  ): Promise<Response> {
    // Proactive refresh check before making the request
    maybeProactiveRefresh();

    // Make the original request
    const response = await fetch(input, {
      ...init,
      credentials: init?.credentials ?? "include",
    });

    // If not a 401, return normally
    if (response.status !== 401) {
      return response;
    }

    // 401 received — attempt token refresh
    const refreshed = await triggerRefresh();

    if (!refreshed) {
      // Refresh failed (session cleared, redirect in progress)
      return response;
    }

    // Retry the original request with the new token
    return fetch(input, {
      ...init,
      credentials: init?.credentials ?? "include",
    });
  }

  return authFetch as typeof fetch;
}
