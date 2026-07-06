import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { renderHook, act } from "@testing-library/react";
import fc from "fast-check";
import { useAudioQueue } from "../useAudioQueue";

// Feature: polly-commentary-tts, Property 6/7/8

/**
 * Property 6: Audio queue FIFO ordering
 *
 * For any sequence of audio segments enqueued while playback is active,
 * the segments SHALL be played back in exactly the order they were enqueued
 * (first-in, first-out).
 *
 * **Validates: Requirements 3.2, 4.1**
 *
 * Property 7: Audio queue deduplication
 *
 * For any text string that is identical to the text currently playing or any
 * text already present in the audio queue, attempting to enqueue it SHALL not
 * modify the queue state.
 *
 * **Validates: Requirements 3.5**
 *
 * Property 8: Audio queue capacity invariant
 *
 * For any sequence of enqueue operations, the number of pending (non-playing)
 * segments in the audio queue SHALL never exceed 5. When a segment arrives and
 * the queue is full, the oldest pending segment SHALL be discarded to make room.
 *
 * **Validates: Requirements 4.3**
 */

// Mock HTMLAudioElement for jsdom environment
class MockAudioElement {
  src = "";
  paused = true;
  private _onended: (() => void) | null = null;
  private _onerror: ((e: unknown) => void) | null = null;

  set onended(handler: (() => void) | null) {
    this._onended = handler;
  }
  get onended() {
    return this._onended;
  }

  set onerror(handler: ((e: unknown) => void) | null) {
    this._onerror = handler;
  }
  get onerror() {
    return this._onerror;
  }

  play(): Promise<void> {
    this.paused = false;
    return Promise.resolve();
  }

  pause() {
    this.paused = true;
  }

  // Test helper: simulate audio finished playing
  simulateEnded() {
    this.paused = true;
    this._onended?.();
  }

  // Test helper: simulate playback error
  simulateError() {
    this.paused = true;
    this._onerror?.(new Error("playback error"));
  }
}

// Track all created Audio instances
let audioInstances: MockAudioElement[] = [];

// Mock URL.createObjectURL and URL.revokeObjectURL
const objectUrls = new Map<Blob, string>();
let urlCounter = 0;

function createMockBlob(text: string): Blob {
  return new Blob([text], { type: "audio/mpeg" });
}

describe("useAudioQueue - Property Tests", () => {
  beforeEach(() => {
    audioInstances = [];
    objectUrls.clear();
    urlCounter = 0;

    // Mock Audio constructor
    (globalThis as unknown as Record<string, unknown>).Audio = vi.fn(() => {
      const instance = new MockAudioElement();
      audioInstances.push(instance);
      return instance;
    });

    // Mock URL.createObjectURL
    vi.spyOn(URL, "createObjectURL").mockImplementation((blob: Blob) => {
      const url = `blob:mock-url-${urlCounter++}`;
      objectUrls.set(blob, url);
      return url;
    });

    // Mock URL.revokeObjectURL
    vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // Feature: polly-commentary-tts, Property 6: Audio queue FIFO ordering
  it("Property 6: enqueued segments are played in FIFO order", () => {
    fc.assert(
      fc.property(
        // Generate a sequence of distinct commentary texts (2 to 6 items)
        fc.uniqueArray(fc.string({ minLength: 1, maxLength: 50 }), {
          minLength: 2,
          maxLength: 6,
        }),
        (texts) => {
          const { result } = renderHook(() => useAudioQueue(true));

          // Enqueue all segments
          act(() => {
            for (const text of texts) {
              result.current.enqueue(text, createMockBlob(text));
            }
          });

          // The first item should be currently playing (or the queue should
          // reflect FIFO order). Collect the played order by simulating
          // sequential playback completions.
          const playedOrder: string[] = [];

          // The first segment should be playing immediately
          const state = result.current.state;
          if (state.currentlyPlaying) {
            playedOrder.push(state.currentlyPlaying.text);
          }

          // Simulate each audio ending to advance through the queue
          for (let i = 0; i < texts.length - 1; i++) {
            const currentAudio = audioInstances[audioInstances.length - 1];
            act(() => {
              currentAudio?.simulateEnded();
            });

            const nextState = result.current.state;
            if (nextState.currentlyPlaying) {
              playedOrder.push(nextState.currentlyPlaying.text);
            }
          }

          // The order played must match the order enqueued
          // (limited to the queue capacity + 1 for currently playing)
          const maxItems = Math.min(texts.length, 6); // 5 queue + 1 playing
          const expectedOrder = texts.slice(0, maxItems);

          // If the queue had to drop items (capacity overflow), the first
          // item is playing and the last 5 are queued, so the played order
          // reflects: first enqueued plays first, remaining are FIFO
          expect(playedOrder.length).toBeGreaterThan(0);

          // Verify FIFO: each played item appears before the next in the original sequence
          for (let i = 1; i < playedOrder.length; i++) {
            const prevIndex = texts.indexOf(playedOrder[i - 1]);
            const currIndex = texts.indexOf(playedOrder[i]);
            expect(currIndex).toBeGreaterThan(prevIndex);
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  // Feature: polly-commentary-tts, Property 7: Audio queue deduplication
  it("Property 7: duplicate text is not enqueued", () => {
    fc.assert(
      fc.property(
        // Generate a non-empty text string and the number of times to attempt enqueueing it
        fc.string({ minLength: 1, maxLength: 50 }),
        fc.integer({ min: 2, max: 10 }),
        (text, repeatCount) => {
          const { result } = renderHook(() => useAudioQueue(true));

          // Enqueue the same text multiple times
          act(() => {
            for (let i = 0; i < repeatCount; i++) {
              result.current.enqueue(text, createMockBlob(text));
            }
          });

          const state = result.current.state;

          // Count total occurrences: queue + currently playing
          let totalOccurrences = 0;
          if (
            state.currentlyPlaying &&
            state.currentlyPlaying.text === text
          ) {
            totalOccurrences++;
          }
          totalOccurrences += state.queue.filter(
            (seg) => seg.text === text
          ).length;

          // The text should appear exactly once (deduplicated)
          expect(totalOccurrences).toBe(1);
        }
      ),
      { numRuns: 100 }
    );
  });

  // Feature: polly-commentary-tts, Property 8: Audio queue capacity invariant
  it("Property 8: pending queue never exceeds 5 segments", () => {
    fc.assert(
      fc.property(
        // Generate a sequence of distinct texts to enqueue (up to 20)
        fc.uniqueArray(fc.string({ minLength: 1, maxLength: 50 }), {
          minLength: 1,
          maxLength: 20,
        }),
        (texts) => {
          const { result } = renderHook(() => useAudioQueue(true));

          // Enqueue all segments, checking the invariant after each
          for (const text of texts) {
            act(() => {
              result.current.enqueue(text, createMockBlob(text));
            });

            // The pending queue (excluding currently playing) must never exceed 5
            const state = result.current.state;
            expect(state.queue.length).toBeLessThanOrEqual(5);
          }

          // Final check: queue capacity invariant holds
          const finalState = result.current.state;
          expect(finalState.queue.length).toBeLessThanOrEqual(5);
        }
      ),
      { numRuns: 100 }
    );
  });

  // Additional property for capacity: when queue is full, oldest is dropped
  it("Property 8 (corollary): when queue is full, new segment replaces oldest", () => {
    fc.assert(
      fc.property(
        // Generate exactly 8 distinct texts (1 plays + 5 fill queue + 2 overflow)
        fc.uniqueArray(fc.string({ minLength: 1, maxLength: 50 }), {
          minLength: 8,
          maxLength: 8,
        }),
        (texts) => {
          const { result } = renderHook(() => useAudioQueue(true));

          // Enqueue all 8 segments
          act(() => {
            for (const text of texts) {
              result.current.enqueue(text, createMockBlob(text));
            }
          });

          const state = result.current.state;

          // First segment should be currently playing
          expect(state.currentlyPlaying).not.toBeNull();
          expect(state.currentlyPlaying!.text).toBe(texts[0]);

          // Queue should have exactly 5 items (capacity limit)
          expect(state.queue.length).toBe(5);

          // The newest segments should be in the queue (oldest dropped)
          // Queue should contain the last 5 enqueued items (texts[3] through texts[7])
          const queueTexts = state.queue.map((seg) => seg.text);
          for (const text of texts.slice(3)) {
            expect(queueTexts).toContain(text);
          }

          // The dropped segments (texts[1] and texts[2]) should not be in the queue
          expect(queueTexts).not.toContain(texts[1]);
          expect(queueTexts).not.toContain(texts[2]);
        }
      ),
      { numRuns: 100 }
    );
  });
});
