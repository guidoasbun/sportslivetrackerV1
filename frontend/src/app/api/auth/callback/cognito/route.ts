import { NextResponse } from 'next/server';
import { exchangeCodeForTokens } from '@/lib/cognito';
import { createSession } from '@/lib/sessions';

export async function GET(request: Request) {
    const { searchParams } = new URL(request.url);
    const code = searchParams.get('code');
    const error = searchParams.get('error');

    if (error) {
        return NextResponse.redirect(new URL(`/login?error=${encodeURIComponent(error)}`, request.url));
    }

    if (!code) {
        return NextResponse.redirect(new URL('/login?error=No+code+provided', request.url));
    }

    try {
        const redirectUri = `${process.env.NEXT_PUBLIC_APP_URL || 'http://localhost:3000'}/api/auth/callback/cognito`;
        const tokens = await exchangeCodeForTokens(code, redirectUri);
        
        // We use the ID token for session auth because it contains user claims (email, name, etc.)
        await createSession(tokens.id_token);

        return NextResponse.redirect(new URL('/dashboard', request.url));
    } catch (error: any) {
        console.error('Error exchanging code for tokens:', error);
        return NextResponse.redirect(new URL(`/login?error=${encodeURIComponent(error.message)}`, request.url));
    }
}
