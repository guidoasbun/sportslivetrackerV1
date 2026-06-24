import React from 'react';

export default function ProtectedLayout({ children }: { children: React.ReactNode }) {
    return (
        <div style={{
            minHeight: '100vh',
            display: 'flex',
            flexDirection: 'column',
            background: '#0a0f16', // Dark, sleek app background
            color: '#ffffff',
            fontFamily: '"Inter", sans-serif'
        }}>
            <main style={{
                flex: 1,
                padding: '32px 24px',
                maxWidth: '1200px',
                margin: '0 auto',
                width: '100%'
            }}>
                {children}
            </main>
        </div>
    );
}
