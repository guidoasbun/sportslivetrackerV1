import { SportEvent } from '@/types/event';
import CommentaryPanel from './CommentaryPanel';
import SportBadge from './SportsBadge';

interface EventFeedProps {
    events: SportEvent[];
}

export default function EventFeed({ events }: EventFeedProps) {
    // If our time-travel buffer is empty, show a nice empty state
    if (events.length === 0) {
        return (
            <div className="flex flex-col items-center justify-center p-12 text-slate-400 bg-slate-900/50 rounded-2xl border border-slate-800">
                <p className="text-lg font-medium">Waiting for live events...</p>
                <p className="text-sm mt-2">Adjust your broadcast delay if you don't see anything.</p>
            </div>
        );
    }

    return (
        <div className="flex flex-col gap-4">
            {events.map((event) => (
                <div
                    key={event.eventId}
                    // Here is the magic Tailwind! 'backdrop-blur-md' gives it the glass look.
                    // 'hover:-translate-y-1' and 'transition-all' make the card physically lift up when hovered!
                    className="group relative bg-slate-800/80 backdrop-blur-md border border-slate-700/50 rounded-xl p-5 hover:bg-slate-800 transition-all duration-300 hover:shadow-lg hover:shadow-indigo-500/10 hover:-translate-y-1"
                >
                    {/* A glowing accent line on the left side that brightens on hover */}
                    <div className="absolute left-0 top-0 bottom-0 w-1 bg-indigo-500 rounded-l-xl opacity-50 group-hover:opacity-100 transition-opacity duration-300" />

                    <div className="flex justify-between items-start mb-2">
                        <SportBadge sportType={event.sportType} />

                        <span className="text-slate-400 text-sm font-mono">
                            {/* Convert epoch timestamp to a readable local time */}
                            {new Date(event.eventTimestamp).toLocaleTimeString()}
                        </span>
                    </div>

                    <h3 className="text-xl font-bold text-white mb-2 capitalize">
                        {event.action.replace('_', ' ')}
                    </h3>

                    <div className="flex flex-wrap gap-2 mt-3">



                        {/* The participants map is dynamic per sport. We loop over the keys and values here */}
                        {Object.entries(event.participants).map(([role, name]) => (
                            <div key={role} className="flex items-center text-sm bg-slate-900/50 px-3 py-1.5 rounded-lg border border-slate-700/50">
                                <span className="text-slate-400 mr-2 capitalize">{role}:</span>
                                <span className="text-slate-200 font-medium">{name}</span>
                            </div>
                        ))}
                    </div>

                    {/* Add the AI Commentary Panel at the bottom of the card */}
                    <CommentaryPanel eventId={event.eventId} />
                </div>
            ))}
        </div>
    );
}
