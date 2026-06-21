// frontend/src/components/layout/Header.tsx
import Link from 'next/link';

export default function Header() {
    return (
        <header className="sticky top-0 z-50 w-full border-b border-slate-800 bg-slate-950/80 backdrop-blur-md">
            <div className="container mx-auto px-4 h-16 flex items-center justify-between">
                <div className="flex items-center gap-2">
                    {/* A simple glowing orb to represent 'Live' */}
                    <div className="w-3 h-3 bg-indigo-500 rounded-full animate-pulse shadow-[0_0_10px_rgba(99,102,241,0.8)]" />
                    <Link href="/" className="text-xl font-black tracking-tight text-white hover:text-indigo-400 transition-colors">
                        SportsLiveTracker
                    </Link>
                </div>

                <nav className="flex items-center gap-6 text-sm font-medium text-slate-300">
                    <Link href="/" className="hover:text-white transition-colors">
                        Dashboard
                    </Link>
                    {/* We'll add Login/Logout buttons here later during the Auth phase! */}
                    <button className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg transition-colors shadow-lg shadow-indigo-500/20">
                        Sign In
                    </button>
                </nav>
            </div>
        </header>
    );
}
