import { cookies } from "next/headers";

// The name of our cookie
const SESSION_COOKIE_NAME = "session";

/**
 * Saves the Cognito token in a secure, HttpOnly cookie.
 * In Next.js 15+, the cookies() API is asynchronous.
 */
export async function createSession(token: string) {
    const cookieStore = await cookies();

    cookieStore.set(SESSION_COOKIE_NAME, token, {
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
 * Retrieves the current session token from the cookie
 */
export async function getSession() {
    const cookieStore = await cookies();
    return cookieStore.get(SESSION_COOKIE_NAME)?.value;
}

/**
 * Clears the session cookie (used for logging out)
 */
export async function deleteSession() {
    const cookieStore = await cookies();
    cookieStore.delete(SESSION_COOKIE_NAME);
}
