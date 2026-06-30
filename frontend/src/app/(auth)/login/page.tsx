"use client";

import { useState, useEffect, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';

function LoginForm() {
    const router = useRouter();
    const searchParams = useSearchParams();
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        const err = searchParams.get('error');
        if (err) {
            setError(decodeURIComponent(err.replace(/\+/g, ' ')));
        }
    }, [searchParams]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setError('');

        try {
            // Hit the API route we created earlier!
            const res = await fetch('/api/auth/email/signin', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, password })
            });

            const data = await res.json();

            if (!res.ok) {
                throw new Error(data.error || 'Login failed');
            }

            // Success! Our HttpOnly cookie is set. 
            // Force a refresh so the middleware sees the new cookie, and route to dashboard.
            router.refresh();
            router.push('/dashboard');

        } catch (err: any) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div>
            <h2 style={{ textAlign: 'center', fontSize: '28px', fontWeight: 'bold', marginBottom: '32px' }}>
                Welcome Back
            </h2>

            {error && (
                <div style={{ background: 'rgba(255, 77, 79, 0.2)', border: '1px solid #ff4d4f', color: '#ff4d4f', padding: '12px', borderRadius: '8px', marginBottom: '20px', fontSize: '14px' }}>
                    {error}
                </div>
            )}

            <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                <div>
                    <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', opacity: 0.8 }}>Email</label>
                    <input
                        type="email"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        required
                        style={{
                            width: '100%', padding: '14px', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.1)',
                            background: 'rgba(255,255,255,0.05)', color: 'white', outline: 'none', fontSize: '16px'
                        }}
                    />
                </div>

                <div>
                    <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', opacity: 0.8 }}>Password</label>
                    <input
                        type="password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        required
                        style={{
                            width: '100%', padding: '14px', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.1)',
                            background: 'rgba(255,255,255,0.05)', color: 'white', outline: 'none', fontSize: '16px'
                        }}
                    />
                    <div style={{ marginTop: '8px', textAlign: 'right' }}>
                        <a href="/forgot-password" style={{ color: '#00b4db', textDecoration: 'none', fontSize: '13px', opacity: 0.9 }}>Forgot password?</a>
                    </div>
                </div>

                <button
                    type="submit"
                    disabled={loading}
                    style={{
                        marginTop: '12px', padding: '16px', borderRadius: '12px', border: 'none',
                        background: loading ? 'rgba(255,255,255,0.1)' : 'linear-gradient(90deg, #00b4db, #0083b0)',
                        color: 'white', fontWeight: 'bold', fontSize: '16px',
                        cursor: loading ? 'not-allowed' : 'pointer', transition: 'all 0.3s ease'
                    }}
                >
                    {loading ? 'Signing in...' : 'Sign In'}
                </button>
            </form>

            <div style={{ marginTop: '20px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                <div style={{ display: 'flex', alignItems: 'center', opacity: 0.5, fontSize: '14px' }}>
                    <div style={{ flex: 1, height: '1px', background: 'white' }}></div>
                    <span style={{ margin: '0 10px' }}>OR</span>
                    <div style={{ flex: 1, height: '1px', background: 'white' }}></div>
                </div>
                <button
                    onClick={() => {
                        const clientId = process.env.NEXT_PUBLIC_COGNITO_CLIENT_ID;
                        const domain = process.env.NEXT_PUBLIC_COGNITO_DOMAIN;
                        
                        if (!clientId || !domain) {
                            console.error("Missing required environment variables for Google OAuth.");
                            alert("Google Login is not configured properly on this environment.");
                            return;
                        }

                        const redirectUri = encodeURIComponent(`${process.env.NEXT_PUBLIC_APP_URL || 'http://localhost:3000'}/api/auth/callback/cognito`);
                        window.location.href = `${domain}/oauth2/authorize?client_id=${clientId}&response_type=code&scope=email+openid+profile&redirect_uri=${redirectUri}&identity_provider=Google`;
                    }}
                    style={{
                        padding: '14px', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.2)',
                        background: 'transparent', color: 'white', fontWeight: 'bold', fontSize: '16px',
                        cursor: 'pointer', transition: 'all 0.3s ease', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '10px'
                    }}
                >
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
                        <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
                        <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
                        <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
                    </svg>
                    Continue with Google
                </button>
            </div>

            <div style={{ marginTop: '24px', textAlign: 'center', fontSize: '14px', opacity: 0.8 }}>
                Don't have an account? <a href="/signup" style={{ color: '#00b4db', textDecoration: 'none', fontWeight: 'bold' }}>Sign up</a>
            </div>
        </div>
    );
}

export default function LoginPage() {
    return (
        <Suspense fallback={<div>Loading...</div>}>
            <LoginForm />
        </Suspense>
    );
}
