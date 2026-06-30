"use client";

import { useState, useEffect, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { validatePassword } from "@/lib/validation";

function ResetPasswordForm() {
    const router = useRouter();
    const searchParams = useSearchParams();
    const email = searchParams.get('email');

    const [code, setCode] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [error, setError] = useState('');
    const [validationErrors, setValidationErrors] = useState<string[]>([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (!email) {
            router.replace('/forgot-password');
        }
    }, [email, router]);

    if (!email) {
        return null;
    }

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setValidationErrors([]);

        // Validate password
        const result = validatePassword(newPassword);
        if (!result.success) {
            setValidationErrors(result.errors.map(err => err.message));
            return;
        }

        setLoading(true);

        try {
            const res = await fetch('/api/auth/email/reset-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, code, newPassword })
            });

            const data = await res.json();

            if (!res.ok) {
                throw new Error(data.error || 'Failed to reset password');
            }

            router.push('/login?message=Password+reset+successful.+Please+sign+in.');
        } catch (err: any) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div>
            <h2 style={{ textAlign: 'center', fontSize: '28px', fontWeight: 'bold', marginBottom: '12px' }}>
                Reset Password
            </h2>
            <p style={{ textAlign: 'center', fontSize: '14px', opacity: 0.7, marginBottom: '32px' }}>
                Enter the code sent to your email and choose a new password.
            </p>

            {error && (
                <div style={{ background: 'rgba(255, 77, 79, 0.2)', border: '1px solid #ff4d4f', color: '#ff4d4f', padding: '12px', borderRadius: '8px', marginBottom: '20px', fontSize: '14px' }}>
                    {error}
                </div>
            )}

            <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
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

                <div>
                    <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', opacity: 0.8 }}>New Password</label>
                    <input
                        type="password"
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        required
                        style={{
                            width: '100%', padding: '14px', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.1)',
                            background: 'rgba(255,255,255,0.05)', color: 'white', outline: 'none', fontSize: '16px'
                        }}
                    />
                    {validationErrors.length > 0 && (
                        <div style={{ marginTop: '8px' }}>
                            {validationErrors.map((msg, i) => (
                                <p key={i} style={{ color: '#ff4d4f', fontSize: '12px', margin: '4px 0' }}>{msg}</p>
                            ))}
                        </div>
                    )}
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
                    {loading ? 'Resetting...' : 'Reset Password'}
                </button>
            </form>

            <div style={{ marginTop: '24px', textAlign: 'center', fontSize: '14px', opacity: 0.8 }}>
                <a href="/forgot-password" style={{ color: '#00b4db', textDecoration: 'none', fontWeight: 'bold' }}>Back to Forgot Password</a>
            </div>
        </div>
    );
}

export default function ResetPasswordPage() {
    return (
        <Suspense fallback={<div style={{ textAlign: 'center' }}>Loading...</div>}>
            <ResetPasswordForm />
        </Suspense>
    );
}
