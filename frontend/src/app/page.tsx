// frontend/src/app/page.tsx
'use client'; // This tells Next.js this is an interactive client component

import { useState } from 'react';
import { useEventBuffer } from '@/lib/useEventBuffer';
import OffsetSlider from '@/components/dashboard/OffsetSlider';
import EventFeed from '@/components/dashboard/EventFeed';

export default function Dashboard() {
  // 1. The state that holds the user's TV delay slider value
  const [offsetSeconds, setOffsetSeconds] = useState(0);

  // 2. Connect to the stream and get the events that should be visible RIGHT NOW
  const { visibleEvents, isConnected, totalBuffered } = useEventBuffer(offsetSeconds);

  return (
    <main className="min-h-screen bg-slate-950 text-slate-200 p-8 font-sans selection:bg-indigo-500/30">
      <div className="max-w-4xl mx-auto">

        {/* Header Area with a premium gradient title */}
        <header className="mb-10 text-center">
          <h1 className="text-4xl md:text-5xl font-black text-transparent bg-clip-text bg-gradient-to-r from-indigo-400 to-cyan-400 mb-4 tracking-tight">
            Sports Live Tracker
          </h1>
          <p className="text-slate-400 text-lg">
            Real-time telemetry synced exactly to your TV broadcast.
          </p>

          {/* Status Indicator Badge */}
          <div className="mt-4 inline-flex items-center gap-2 bg-slate-900/50 px-4 py-2 rounded-full border border-slate-800">
            <span className="text-sm font-medium text-slate-400">Stream Status:</span>
            <div className={`flex items-center gap-2 ${isConnected ? 'text-emerald-400' : 'text-rose-400'}`}>
              <div className={`w-2 h-2 rounded-full ${isConnected ? 'bg-emerald-400 animate-pulse' : 'bg-rose-400'}`} />
              <span className="text-sm font-bold">{isConnected ? 'LIVE' : 'DISCONNECTED'}</span>
            </div>
            <span className="text-slate-600 mx-2">|</span>
            <span className="text-sm text-slate-400">Events in Memory: {totalBuffered}</span>
          </div>
        </header>

        {/* Dashboard Grid layout */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">

          {/* Left Column: Controls (Our Slider) */}
          <div className="lg:col-span-1 space-y-6">
            <OffsetSlider
              offsetSeconds={offsetSeconds}
              onChange={setOffsetSeconds}
            />
          </div>

          {/* Right Column: The Feed */}
          <div className="lg:col-span-2">
            <h2 className="text-xl font-bold text-white mb-4 flex items-center gap-2">
              <svg className="w-5 h-5 text-indigo-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
              Live Event Feed
            </h2>
            <EventFeed events={visibleEvents} />
          </div>

        </div>
      </div>
    </main>
  );
}
