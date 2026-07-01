"use client";

export default function EventFeedSkeleton() {
  return (
    <div className="flex flex-col gap-4">
      {[1, 2, 3].map((i) => (
        <div
          key={i}
          className="relative bg-slate-800/80 border border-slate-700/50 rounded-xl p-5"
        >
          {/* Left accent bar */}
          <div className="absolute left-0 top-0 bottom-0 w-1 bg-slate-700 rounded-l-xl" />

          {/* Top row: sport badge + timestamp */}
          <div className="flex justify-between items-start mb-2">
            <div className="h-6 w-20 bg-slate-700 rounded-full animate-pulse" />
            <div className="h-4 w-16 bg-slate-700 rounded animate-pulse" />
          </div>

          {/* Action title */}
          <div className="h-6 w-40 bg-slate-700 rounded animate-pulse mb-2" />

          {/* Participant chips */}
          <div className="flex flex-wrap gap-2 mt-3">
            <div className="h-8 w-28 bg-slate-800 rounded-lg border border-slate-700/50 animate-pulse" />
            <div className="h-8 w-32 bg-slate-800 rounded-lg border border-slate-700/50 animate-pulse" />
          </div>
        </div>
      ))}
    </div>
  );
}
