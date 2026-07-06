import { describe, it, expect, beforeEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import fc from "fast-check";
import { useTtsPreference } from "../useTtsPreference";

// Feature: polly-commentary-tts, Property 5: TTS preference localStorage round-trip

/**
 * Property 5: TTS preference localStorage round-trip
 *
 * For any boolean TTS preference value, persisting it to localStorage
 * and then reading it back SHALL produce the original value.
 *
 * **Validates: Requirements 2.4**
 */

const STORAGE_KEY = "gameshift-tts-enabled";

describe("useTtsPreference - Property Tests", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("Property 5: persisting a boolean preference and reading it back produces the original value", () => {
    fc.assert(
      fc.property(fc.boolean(), (preferenceValue) => {
        // Clear localStorage before each iteration
        localStorage.clear();

        // Set the preference in localStorage as the hook would
        localStorage.setItem(STORAGE_KEY, String(preferenceValue));

        // Render the hook — it should read the stored value on mount
        const { result } = renderHook(() => useTtsPreference());

        // The hook's enabled state must match the persisted value
        expect(result.current.enabled).toBe(preferenceValue);
      }),
      { numRuns: 100 }
    );
  });

  it("Property 5: toggling the preference persists the new value to localStorage", () => {
    fc.assert(
      fc.property(fc.boolean(), (initialValue) => {
        // Clear localStorage before each iteration
        localStorage.clear();

        // Set initial preference
        localStorage.setItem(STORAGE_KEY, String(initialValue));

        // Render the hook
        const { result } = renderHook(() => useTtsPreference());

        // Toggle the preference
        act(() => {
          result.current.toggle();
        });

        // The new enabled state should be the negation of the initial value
        const expectedAfterToggle = !initialValue;
        expect(result.current.enabled).toBe(expectedAfterToggle);

        // localStorage should reflect the new value
        expect(localStorage.getItem(STORAGE_KEY)).toBe(
          String(expectedAfterToggle)
        );
      }),
      { numRuns: 100 }
    );
  });
});
