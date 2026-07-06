import { useState, useCallback, useRef, useEffect } from "react";

export interface AudioSegment {
  id: string;
  text: string;
  audioBlob: Blob;
}

export interface AudioQueueState {
  queue: AudioSegment[];
  currentlyPlaying: AudioSegment | null;
  isPlaying: boolean;
}

const MAX_QUEUE_SIZE = 5;

/**
 * Custom React hook managing the audio playback queue.
 * Plays audio segments one at a time in FIFO order.
 * Deduplicates by text, enforces max capacity of 5 pending segments.
 * When enabled becomes false, stops playback and clears the queue.
 */
export function useAudioQueue(enabled: boolean): {
  enqueue: (text: string, audioBlob: Blob) => void;
  skip: () => void;
  stopAll: () => void;
  state: AudioQueueState;
} {
  const [state, setState] = useState<AudioQueueState>({
    queue: [],
    currentlyPlaying: null,
    isPlaying: false,
  });

  const audioRef = useRef<HTMLAudioElement | null>(null);
  const objectUrlRef = useRef<string | null>(null);

  // Cleanup object URL
  const cleanupObjectUrl = useCallback(() => {
    if (objectUrlRef.current) {
      URL.revokeObjectURL(objectUrlRef.current);
      objectUrlRef.current = null;
    }
  }, []);

  // Play the next segment from the queue
  const playNext = useCallback(() => {
    setState((prev) => {
      if (prev.queue.length === 0) {
        return { ...prev, currentlyPlaying: null, isPlaying: false };
      }
      const [next, ...rest] = prev.queue;
      return { queue: rest, currentlyPlaying: next, isPlaying: true };
    });
  }, []);

  // When currentlyPlaying changes, start audio playback
  useEffect(() => {
    if (!state.currentlyPlaying) {
      if (audioRef.current) {
        audioRef.current.pause();
        audioRef.current = null;
      }
      cleanupObjectUrl();
      return;
    }

    cleanupObjectUrl();

    const url = URL.createObjectURL(state.currentlyPlaying.audioBlob);
    objectUrlRef.current = url;

    const audio = new Audio(url);
    audioRef.current = audio;

    audio.onended = () => {
      playNext();
    };

    audio.onerror = () => {
      console.warn("Audio playback error, skipping segment");
      playNext();
    };

    audio.play().catch(() => {
      console.warn("Audio play() rejected, skipping segment");
      playNext();
    });
  }, [state.currentlyPlaying, cleanupObjectUrl, playNext]);

  // When enabled becomes false, stop and clear
  useEffect(() => {
    if (!enabled) {
      if (audioRef.current) {
        audioRef.current.pause();
        audioRef.current = null;
      }
      cleanupObjectUrl();
      setState({ queue: [], currentlyPlaying: null, isPlaying: false });
    }
  }, [enabled, cleanupObjectUrl]);

  const enqueue = useCallback((text: string, audioBlob: Blob) => {
    setState((prev) => {
      // Deduplication: check if text matches currently playing or any queued item
      if (prev.currentlyPlaying?.text === text) {
        return prev;
      }
      if (prev.queue.some((seg) => seg.text === text)) {
        return prev;
      }

      const segment: AudioSegment = {
        id: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
        text,
        audioBlob,
      };

      // If nothing is currently playing, start playing immediately
      if (!prev.currentlyPlaying) {
        return { queue: prev.queue, currentlyPlaying: segment, isPlaying: true };
      }

      // Enforce capacity: drop oldest if full
      let newQueue = [...prev.queue, segment];
      if (newQueue.length > MAX_QUEUE_SIZE) {
        newQueue = newQueue.slice(newQueue.length - MAX_QUEUE_SIZE);
      }

      return { ...prev, queue: newQueue };
    });
  }, []);

  const skip = useCallback(() => {
    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current = null;
    }
    cleanupObjectUrl();
    playNext();
  }, [cleanupObjectUrl, playNext]);

  const stopAll = useCallback(() => {
    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current = null;
    }
    cleanupObjectUrl();
    setState({ queue: [], currentlyPlaying: null, isPlaying: false });
  }, [cleanupObjectUrl]);

  return { enqueue, skip, stopAll, state };
}
