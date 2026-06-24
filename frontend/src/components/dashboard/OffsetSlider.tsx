// frontend/src/components/dashboard/OffsetSlider.tsx

interface OffsetSliderProps {
    offsetSeconds: number;
    onChange: (newOffset: number) => void;
}

export default function OffsetSlider({ offsetSeconds, onChange }: OffsetSliderProps) {
    // Let's set a maximum allowable delay of 120 seconds (2 minutes)
    const MAX_DELAY = 120;

    return (
        <div className="bg-slate-800/80 backdrop-blur-md border border-slate-700/50 rounded-xl p-6 shadow-xl">

            {/* Header section with the title and the current value display */}
            <div className="flex justify-between items-center mb-4">
                <div>
                    <h2 className="text-lg font-bold text-white">Broadcast Delay Sync</h2>
                    <p className="text-xs text-slate-400 mt-1">Match this to your TV's delay</p>
                </div>

                {/* A cool digital-looking box to display the current seconds */}
                <div className="bg-indigo-500/20 border border-indigo-500/30 px-4 py-2 rounded-lg text-center min-w-[80px]">
                    <span className="block text-2xl font-black text-indigo-400 leading-none">
                        {offsetSeconds}
                    </span>
                    <span className="text-[10px] uppercase font-bold text-indigo-300 tracking-widest mt-1 block">
                        Seconds
                    </span>
                </div>
            </div>

            {/* The actual slider input */}
            <div className="relative pt-2">
                <input
                    type="range"
                    min="0"
                    max={MAX_DELAY}
                    value={offsetSeconds}
                    // When the slider moves, we convert the string value to a Number and send it back up!
                    onChange={(e) => onChange(Number(e.target.value))}
                    className="w-full h-2 bg-slate-700 rounded-lg appearance-none cursor-pointer accent-indigo-500 hover:accent-indigo-400 transition-all"
                />
                <div className="flex justify-between text-xs text-slate-500 mt-2 font-mono">
                    <span>Live (0s)</span>
                    <span>Max (120s)</span>
                </div>
            </div>
        </div>
    );
}
