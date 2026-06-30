import { cookies } from "next/headers";

// The name of our cookie
const SESSION_COOKIE_NAME = "session";

/**
 * Session data structure for storing both access and refresh tokens.
 */
export interface SessionData {
    accessToken: string;
    refreshToken: string;
    expiresAt?: number; // epoch seconds from JWT exp claim
}

/**
 * Saves the session in a secure, HttpOnly cookie.
 * Accepts either a SessionData object (new format) or a plain token string (backward compat).
 * In Next.js 15+, the cookies() API is asynchronous.
 */
export async function createSession(tokenOrData: string | SessionData) {
    const cookieStore = await cookies();

    const value =
        typeof tokenOrData === "string"
            ? tokenOrData
            : JSON.stringify(tokenOrData);

    cookieStore.set(SESSION_COOKIE_NAME, value, {
        httpOnly: true,
        secure: process.env.NODE_ENV === "production", // Only require HTTPS in production
        sameSite: "lax",
        path: "/",
        // Cognito Access/Id tokens generally last 1 hour by default,
        // but we'll set the cookie to live for a day so it doesn't expire too early on the client
        maxAge: 60 * 60 * 24,
    });
}

/**
 * Retrieves the current session from the cookie.
 * Returns SessionData if the cookie contains a valid JSON object with tokens,
 * or a plain string for backward compatibility with old single-token sessions.
 */
export async function getSession(): Promise<SessionData | string | undefined> {
    const cookieStore = await cookies();
    const raw = cookieStore.get(SESSION_COOKIE_NAME)?.value;

    if (!raw) {
        return undefined;
    }

    // Try to parse as JSON SessionData
    try {
        const parsed = JSON.parse(raw);
        if (
            parsed &&
            typeof parsed === "object" &&
            typeof parsed.accessToken === "string" &&
            typeof parsed.refreshToken === "string"
        ) {
            return parsed as SessionData;
        }
    } catch {
        // Not JSON — fall through to return raw string
    }

    // Backward compat: return the plain token string
    return raw;
}

/**
 * Updates just the access token in an existing session.
 * Used by the token refresh flow to store the new access token
 * while preserving the refresh token.
 * If the session is in the old string format, it will be preserved as-is
 * (caller should migrate to SessionData format via createSession).
 */
export async function updateSessionTokens(accessToken: string) {
    const session = await getSession();

    if (session && typeof session === "object") {
        // Session is already in SessionData format — update accessToken
        await createSession({
            ...session,
            accessToken,
        });
    } else {
        // Old format or no session — just store the new access token as plain string
        await createSession(accessToken);
    }
}

/**
 * Clears the session cookie (used for logging out)
 */
export async function deleteSession() {
    const cookieStore = await cookies();
    cookieStore.delete(SESSION_COOKIE_NAME);
}
