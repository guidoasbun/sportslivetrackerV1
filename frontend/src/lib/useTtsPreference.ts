import { useState, useEffect, useCallback } from "react";

const STORAGE_KEY = "gameshift-tts-enabled";

/**
 * Hook to manage TTS toggle state with localStorage persistence.
 * Restores stored preference on mount; defaults to false if not found or localStorage unavailable.
 */
export function useTtsPreference(): { enabled: boolean; toggle: () => void } {
  const [enabled, setEnabled] = useState<boolean>(() => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored === "true") return true;
      if (stored === "false") return false;
      return false;
    } catch {
      return false;
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, String(enabled));
    } catch {
      // localStorage unavailable — preference not persisted
    }
  }, [enabled]);

  const toggle = useCallback(() => {
    setEnabled((prev) => !prev);
  }, []);

  return { enabled, toggle };
}
