import { NextResponse } from 'next/server';
import { exchangeCodeForTokens } from '@/lib/cognito';
import { createSession } from '@/lib/sessions';

export async function GET(request: Request) {
    const { searchParams } = new URL(request.url);
    const code = searchParams.get('code');
    const error = searchParams.get('error');

    const appUrl = process.env.NEXT_PUBLIC_APP_URL || 'http://localhost:3000';

    if (error) {
        return NextResponse.redirect(new URL(`/login?error=${encodeURIComponent(error)}`, appUrl));
    }

    if (!code) {
        return NextResponse.redirect(new URL('/login?error=No+code+provided', appUrl));
    }

    try {
        const redirectUri = `${process.env.NEXT_PUBLIC_APP_URL || 'http://localhost:3000'}/api/auth/callback/cognito`;
        const tokens = await exchangeCodeForTokens(code, redirectUri);
        
        // Store both tokens so token refresh can work
        await createSession({
            accessToken: tokens.access_token,
            refreshToken: tokens.refresh_token,
        });

        return NextResponse.redirect(new URL('/dashboard', appUrl));
    } catch (error: any) {
        console.error('Error exchanging code for tokens:', error);
        return NextResponse.redirect(new URL(`/login?error=${encodeURIComponent(error.message)}`, appUrl));
    }
}
