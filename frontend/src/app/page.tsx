import Link from 'next/link';

export default function LandingPage() {
  return (
    <div className="flex flex-col items-center justify-center min-h-[80vh] text-center px-4">
      
      {/* Hero Section */}
      <div className="max-w-3xl space-y-8">
        <h1 className="text-5xl md:text-7xl font-extrabold tracking-tight text-transparent bg-clip-text bg-gradient-to-r from-indigo-400 via-cyan-400 to-emerald-400">
          Live Sports, <br /> Synced to Your Screen.
        </h1>
        
        <p className="text-lg md:text-xl text-slate-400 max-w-2xl mx-auto leading-relaxed">
          The ultimate real-time telemetry dashboard. With SportsLiveTracker, you can artificially delay our live data feed to match your TV broadcast down to the exact second. Say goodbye to spoilers.
        </p>

        <div className="pt-8 flex flex-col sm:flex-row items-center justify-center gap-4">
          <Link 
            href="/signup"
            className="px-8 py-4 bg-indigo-500 hover:bg-indigo-600 text-white font-bold rounded-full transition-all shadow-lg shadow-indigo-500/25 hover:shadow-indigo-500/40 hover:-translate-y-1 w-full sm:w-auto"
          >
            Get Started Free
          </Link>
          
          <Link 
            href="/login"
            className="px-8 py-4 bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-200 font-bold rounded-full transition-all w-full sm:w-auto"
          >
            Log In
          </Link>
        </div>
      </div>

      {/* Feature Preview */}
      <div className="mt-24 grid grid-cols-1 md:grid-cols-3 gap-8 max-w-5xl w-full text-left">
        <div className="bg-slate-900/50 border border-slate-800 p-6 rounded-2xl">
          <div className="w-12 h-12 bg-indigo-500/20 text-indigo-400 rounded-xl flex items-center justify-center mb-4">
            <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <h3 className="text-xl font-bold text-slate-200 mb-2">Time-Travel Sync</h3>
          <p className="text-slate-400 text-sm">Slider-based offset controls ensure our data arrives at the exact moment you see the play happen on your screen.</p>
        </div>

        <div className="bg-slate-900/50 border border-slate-800 p-6 rounded-2xl">
          <div className="w-12 h-12 bg-cyan-500/20 text-cyan-400 rounded-xl flex items-center justify-center mb-4">
            <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
          </div>
          <h3 className="text-xl font-bold text-slate-200 mb-2">Real-Time Streams</h3>
          <p className="text-slate-400 text-sm">Powered by Server-Sent Events (SSE) from our blazing fast Java Spring Boot backend, bypassing typical REST bottlenecks.</p>
        </div>

        <div className="bg-slate-900/50 border border-slate-800 p-6 rounded-2xl">
          <div className="w-12 h-12 bg-emerald-500/20 text-emerald-400 rounded-xl flex items-center justify-center mb-4">
            <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
            </svg>
          </div>
          <h3 className="text-xl font-bold text-slate-200 mb-2">AI Color Commentary</h3>
          <p className="text-slate-400 text-sm">Rich, dynamic summaries of events generated instantly by AWS Bedrock for every major play.</p>
        </div>
      </div>
    </div>
  );
}
