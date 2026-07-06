"use client";

export interface PlaybackControlsProps {
  isPlaying: boolean;
  queueLength: number;
  onSkip: () => void;
  onStopAll: () => void;
  onPlayNext: () => void;
}

/**
 * Playback control buttons for the TTS audio queue.
 * Shows Skip/Stop All while playing, Play Next/Stop All when queue has items,
 * and hides entirely when idle and queue is empty.
 */
export default function PlaybackControls({
  isPlaying,
  queueLength,
  onSkip,
  onStopAll,
  onPlayNext,
}: PlaybackControlsProps) {
  // Hide all controls when not playing and queue is empty
  if (!isPlaying && queueLength === 0) {
    return null;
  }

  return (
    <div className="flex items-center gap-1">
      {isPlaying ? (
        <button
          type="button"
          aria-label="Skip current commentary"
          title="Skip current commentary"
          onClick={onSkip}
          className="inline-flex items-center justify-center rounded-md p-2 text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-700"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 24 24"
            fill="currentColor"
            className="h-5 w-5"
            aria-hidden="true"
          >
            <path d="M5.055 7.06C3.805 6.347 2.25 7.25 2.25 8.69v6.62c0 1.44 1.555 2.343 2.805 1.628L12 12.756V8.69c0-1.44-1.555-2.343-2.805-1.628L5.055 7.06z" />
            <path d="M12.055 7.06C10.805 6.347 9.25 7.25 9.25 8.69v6.62c0 1.44 1.555 2.343 2.805 1.628L19 12.756V8.69c0-1.44-1.555-2.343-2.805-1.628L12.055 7.06z" />
            <path fillRule="evenodd" d="M19.75 7.5a.75.75 0 01.75.75v7.5a.75.75 0 01-1.5 0v-7.5a.75.75 0 01.75-.75z" clipRule="evenodd" />
          </svg>
        </button>
      ) : (
        <button
          type="button"
          aria-label="Play next commentary"
          title="Play next commentary"
          onClick={onPlayNext}
          className="inline-flex items-center justify-center rounded-md p-2 text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-700"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 24 24"
            fill="currentColor"
            className="h-5 w-5"
            aria-hidden="true"
          >
            <path
              fillRule="evenodd"
              d="M4.5 5.653c0-1.426 1.529-2.33 2.779-1.643l11.54 6.348c1.295.712 1.295 2.573 0 3.285L7.28 19.991c-1.25.687-2.779-.217-2.779-1.643V5.653z"
              clipRule="evenodd"
            />
          </svg>
        </button>
      )}

      <button
        type="button"
        aria-label="Stop all commentary audio"
        title="Stop all commentary audio"
        onClick={onStopAll}
        className="inline-flex items-center justify-center rounded-md p-2 text-gray-500 transition-colors hover:bg-red-50 hover:text-red-600"
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 24 24"
          fill="currentColor"
          className="h-5 w-5"
          aria-hidden="true"
        >
          <path
            fillRule="evenodd"
            d="M4.5 7.5a3 3 0 013-3h9a3 3 0 013 3v9a3 3 0 01-3 3h-9a3 3 0 01-3-3v-9z"
            clipRule="evenodd"
          />
        </svg>
      </button>
    </div>
  );
}
