"use client";

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { validateEmail } from "@/lib/validation";

function ForgotPasswordForm() {
    const router = useRouter();
    const [email, setEmail] = useState('');
    const [error, setError] = useState('');
    const [validationErrors, setValidationErrors] = useState<string[]>([]);
    const [loading, setLoading] = useState(false);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setValidationErrors([]);

        // Validate email
        const result = validateEmail(email);
        if (!result.success) {
            setValidationErrors(result.errors.map(err => err.message));
            return;
        }

        setLoading(true);

        try {
            const res = await fetch('/api/auth/email/forgot-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email })
            });

            const data = await res.json();

            if (!res.ok) {
                throw new Error(data.error || 'Something went wrong');
            }

            router.push(`/reset-password?email=${encodeURIComponent(email)}`);
        } catch (err: any) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div>
            <h2 style={{ textAlign: 'center', fontSize: '28px', fontWeight: 'bold', marginBottom: '12px' }}>
                Forgot Password
            </h2>
            <p style={{ textAlign: 'center', fontSize: '14px', opacity: 0.7, marginBottom: '32px' }}>
                Enter your email and we&apos;ll send you a code to reset your password.
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
                    {loading ? 'Sending...' : 'Send Reset Code'}
                </button>
            </form>

            <div style={{ marginTop: '24px', textAlign: 'center', fontSize: '14px', opacity: 0.8 }}>
                <a href="/login" style={{ color: '#00b4db', textDecoration: 'none', fontWeight: 'bold' }}>Back to Login</a>
            </div>
        </div>
    );
}

export default function ForgotPasswordPage() {
    return <ForgotPasswordForm />;
}
