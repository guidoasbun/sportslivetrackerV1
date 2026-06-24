"use client";

import { useRouter } from 'next/navigation';

export default function LogoutButton() {
    const router = useRouter();

    const handleLogout = async () => {
        // Hit our API route to clear the HttpOnly cookie
        await fetch('/api/auth/logout', { method: 'POST' });
        
        // Send the user back to the landing page and force a refresh of Server Components
        router.push('/');
        router.refresh();
    };

    return (
        <button 
            onClick={handleLogout}
            className="px-4 py-2 bg-rose-600/10 hover:bg-rose-600/20 text-rose-500 hover:text-rose-400 font-medium rounded-lg transition-colors border border-rose-600/20 hover:border-rose-600/30"
        >
            Log Out
        </button>
    );
}
