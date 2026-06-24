import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { getSession } from './lib/sessions'; // Note the 's' matching your filename!

export async function proxy(request: NextRequest) {
    // Get the session from our HttpOnly cookie
    const session = await getSession();

    const { pathname } = request.nextUrl;

    // Define which paths require the user to be logged in
    const isProtectedPath = pathname.startsWith('/dashboard');

    // Define which paths are meant for logged-out users (like login/signup)
    const isAuthPath = pathname.startsWith('/login') ||
        pathname.startsWith('/signup') ||
        pathname.startsWith('/confirm');

    // Rule 1: Not logged in? Redirect away from protected pages to login.
    if (isProtectedPath && !session) {
        return NextResponse.redirect(new URL('/login', request.url));
    }

    // Rule 2: Already logged in? Redirect away from auth pages to the dashboard.
    if (isAuthPath && session) {
        return NextResponse.redirect(new URL('/dashboard', request.url));
    }

    // Otherwise, let them through
    return NextResponse.next();
}

// This config tells Next.js which routes to run the middleware on.
// We exclude static files and API routes to save performance.
export const config = {
    matcher: ['/((?!api|_next/static|_next/image|favicon.ico).*)'],
};
