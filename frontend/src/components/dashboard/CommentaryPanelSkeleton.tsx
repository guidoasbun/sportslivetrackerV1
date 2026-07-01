"use client";

export default function CommentaryPanelSkeleton() {
  return (
    <div className="mt-4 p-4 bg-indigo-900/20 rounded-lg border border-indigo-500/30">
      {/* Header row: label + icon */}
      <div className="flex items-center gap-2 mb-2">
        <div className="h-3 w-24 bg-slate-700 rounded animate-pulse" />
        <div className="h-4 w-4 bg-slate-700 rounded animate-pulse" />
      </div>

      {/* Commentary text lines */}
      <div className="space-y-2">
        <div className="h-4 w-full bg-slate-700 rounded animate-pulse" />
        <div className="h-4 w-3/4 bg-slate-700 rounded animate-pulse" />
      </div>
    </div>
  );
}
