// frontend/src/components/layout/Footer.tsx
export default function Footer() {
    return (
        <footer className="border-t border-slate-800 bg-slate-950 py-8 mt-auto">
            <div className="container mx-auto px-4 text-center text-slate-500 text-sm">
                <p>© {new Date().getFullYear()} SportsLiveTracker. Real-time telemetry synced to your broadcast.</p>
                <p className="mt-2 text-slate-600 font-mono text-xs">Powered by AWS, Next.js, and Spring Boot.</p>
            </div>
        </footer>
    );
}
