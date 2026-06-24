"use client";

import { useState, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';

function ConfirmForm() {
    const router = useRouter();
    const searchParams = useSearchParams();
    const initialEmail = searchParams.get('email') || '';

    const [email, setEmail] = useState(initialEmail);
    const [code, setCode] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setError('');

        try {
            const res = await fetch('/api/auth/email/confirm', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, code })
            });

            const data = await res.json();

            if (!res.ok) {
                throw new Error(data.error || 'Invalid code');
            }

            // Success! Account is confirmed. Send them to login.
            router.push('/login');

        } catch (err: any) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div>
            <h2 style={{ textAlign: 'center', fontSize: '28px', fontWeight: 'bold', marginBottom: '16px' }}>
                Confirm Email
            </h2>
            <p style={{ textAlign: 'center', opacity: 0.8, marginBottom: '32px', fontSize: '14px' }}>
                Please enter the verification code sent to your email.
            </p>

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
                    <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', opacity: 0.8 }}>Verification Code</label>
                    <input
                        type="text"
                        value={code}
                        onChange={(e) => setCode(e.target.value)}
                        required
                        placeholder="123456"
                        style={{
                            width: '100%', padding: '14px', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.1)',
                            background: 'rgba(255,255,255,0.05)', color: 'white', outline: 'none', fontSize: '16px', letterSpacing: '2px'
                        }}
                    />
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
                    {loading ? 'Confirming...' : 'Confirm Account'}
                </button>
            </form>
        </div>
    );
}

// Wrapper component to provide the Suspense boundary Next.js requires
export default function ConfirmPage() {
    return (
        <Suspense fallback={<div style={{ textAlign: 'center' }}>Loading...</div>}>
            <ConfirmForm />
        </Suspense>
    );
}
