import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useAudioQueue } from "../useAudioQueue";

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

  simulateEnded() {
    this.paused = true;
    this._onended?.();
  }

  simulateError() {
    this.paused = true;
    this._onerror?.(new Error("playback error"));
  }
}

// Track all created Audio instances
let audioInstances: MockAudioElement[] = [];

function createMockBlob(text: string): Blob {
  return new Blob([text], { type: "audio/mpeg" });
}

describe("useAudioQueue - Unit Tests", () => {
  beforeEach(() => {
    audioInstances = [];

    (globalThis as unknown as Record<string, unknown>).Audio = vi.fn(
      function (this: MockAudioElement, src?: string) {
        const instance = new MockAudioElement();
        if (src) instance.src = src;
        audioInstances.push(instance);
        return instance;
      }
    );

    vi.spyOn(URL, "createObjectURL").mockImplementation(
      () => `blob:mock-url-${Math.random()}`
    );
    vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // Requirement 4.1: FIFO playback order
  describe("FIFO playback order", () => {
    it("plays segments in the order they were enqueued", () => {
      const { result } = renderHook(() => useAudioQueue(true));

      act(() => {
        result.current.enqueue("first", createMockBlob("first"));
        result.current.enqueue("second", createMockBlob("second"));
        result.current.enqueue("third", createMockBlob("third"));
      });

      // First segment should be currently playing
      expect(result.current.state.currentlyPlaying?.text).toBe("first");
      expect(result.current.state.isPlaying).toBe(true);

      // Simulate first audio ending
      act(() => {
        audioInstances[audioInstances.length - 1].simulateEnded();
      });

      expect(result.current.state.currentlyPlaying?.text).toBe("second");

      // Simulate second audio ending
      act(() => {
        audioInstances[audioInstances.length - 1].simulateEnded();
      });

      expect(result.current.state.currentlyPlaying?.text).toBe("third");

      // Simulate third audio ending
      act(() => {
        audioInstances[audioInstances.length - 1].simulateEnded();
      });

      expect(result.current.state.currentlyPlaying).toBeNull();
      expect(result.current.state.isPlaying).toBe(false);
    });

    it("starts playing immediately when queue is empty", () => {
      const { result } = renderHook(() => useAudioQueue(true));

      act(() => {
        result.current.enqueue("hello", createMockBlob("hello"));
      });

      expect(result.current.state.currentlyPlaying?.text).toBe("hello");
      expect(result.current.state.isPlaying).toBe(true);
      expect(result.current.state.queue).toHaveLength(0);
    });
  });

  // Requirement 4.3: Queue capacity limit of 5
  describe("queue capacity limit", () => {
    it("never exceeds 5 pending segments in the queue", () => {
      const { result } = renderHook(() => useAudioQueue(true));

      // Enqueue 8 segments: 1 plays immediately, queue should cap at 5
      act(() => {
        for (let i = 0; i < 8; i++) {
          result.current.enqueue(`seg-${i}`, createMockBlob(`seg-${i}`));
        }
      });

      expect(result.current.state.currentlyPlaying?.text).toBe("seg-0");
      expect(result.current.state.queue.length).toBe(5);
    });

    it("drops oldest pending segments when capacity exceeded", () => {
      const { result } = renderHook(() => useAudioQueue(true));

      // Enqueue 8 segments
      act(() => {
        for (let i = 0; i < 8; i++) {
          result.current.enqueue(`seg-${i}`, createMockBlob(`seg-${i}`));
        }
      });

      // First is playing, queue should contain the last 5 enqueued (seg-3 through seg-7)
      const queueTexts = result.current.state.queue.map((s) => s.text);
      expect(queueTexts).toEqual(["seg-3", "seg-4", "seg-5", "seg-6", "seg-7"]);
    });
  });

  // Requirement 3.5: Deduplication of identical text
  describe("deduplication", () => {
    it("does not enqueue text that is currently playing", () => {
      const { result } = renderHook(() => useAudioQueue(true));

      act(() => {
        result.current.enqueue("hello", createMockBlob("hello"));
        result.current.enqueue("hello", createMockBlob("hello"));
      });

      expect(result.current.state.currentlyPlaying?.text).toBe("hello");
      expect(result.current.state.queue).toHaveLength(0);
    });

    it("does not enqueue text that is already in the queue", () => {
      const { result } = renderHook(() => useAudioQueue(true));

      act(() => {
        result.current.enqueue("first", createMockBlob("first"));
        result.current.enqueue("second", createMockBlob("second"));
        result.current.enqueue("second", createMockBlob("second"));
        result.current.enqueue("third", createMockBlob("third"));
      });

      expect(result.current.state.queue.map((s) => s.text)).toEqual([
        "second",
        "third",
      ]);
    });

    it("allows re-enqueueing text after it finishes playing", () => {
      const { result } = renderHook(() => useAudioQueue(true));

      act(() => {
        result.current.enqueue("hello", createMockBlob("hello"));
      });

      // Simulate playback ending
      act(() => {
        audioInstances[audioInstances.length - 1].simulateEnded();
      });

      expect(result.current.state.currentlyPlaying).toBeNull();

      // Re-enqueue same text — should work now
      act(() => {
        result.current.enqueue("hello", createMockBlob("hello"));
      });

      expect(result.current.state.currentlyPlaying?.text).toBe("hello");
    });
  });

  // Requirement 4.4: Skip advances to next
  describe("skip", () => {
    it("advances to the next segment in queue", () => {
      const { result } = renderHook(() => useAudioQueue(true));

      act(() => {
        result.current.enqueue("first", createMockBlob("first"));
        result.current.enqueue("second", createMockBlob("second"));
        result.current.enqueue("third", createMockBlob("third"));
      });

      expect(result.current.state.currentlyPlaying?.text).toBe("first");

      act(() => {
        result.current.skip();
      });

      expect(result.current.state.currentlyPlaying?.text).toBe("second");
    });

    it("stops playback when skipping with empty queue", () => {
      const { result } = renderHook(() => useAudioQueue(true));

      act(() => {
        result.current.enqueue("only", createMockBlob("only"));
      });

      expect(result.current.state.currentlyPlaying?.text).toBe("only");

      act(() => {
        result.current.skip();
      });

      expect(result.current.state.currentlyPlaying).toBeNull();
      expect(result.current.state.isPlaying).toBe(false);
    });
  });

  // Requirement 4.5: stopAll clears queue
  describe("stopAll", () => {
    it("stops current playback and clears the queue", () => {
      const { result } = renderHook(() => useAudioQueue(true));

      act(() => {
        result.current.enqueue("first", createMockBlob("first"));
        result.current.enqueue("second", createMockBlob("second"));
        result.current.enqueue("third", createMockBlob("third"));
      });

      expect(result.current.state.isPlaying).toBe(true);
      expect(result.current.state.queue.length).toBe(2);

      act(() => {
        result.current.stopAll();
      });

      expect(result.current.state.currentlyPlaying).toBeNull();
      expect(result.current.state.isPlaying).toBe(false);
      expect(result.current.state.queue).toHaveLength(0);
    });

    it("is a no-op when nothing is playing", () => {
      const { result } = renderHook(() => useAudioQueue(true));

      act(() => {
        result.current.stopAll();
      });

      expect(result.current.state.currentlyPlaying).toBeNull();
      expect(result.current.state.isPlaying).toBe(false);
      expect(result.current.state.queue).toHaveLength(0);
    });
  });

  // Requirement 4.2: Disabled state stops and clears
  describe("disabled state", () => {
    it("stops playback and clears queue when enabled becomes false", () => {
      const { result, rerender } = renderHook(
        ({ enabled }) => useAudioQueue(enabled),
        { initialProps: { enabled: true } }
      );

      act(() => {
        result.current.enqueue("first", createMockBlob("first"));
        result.current.enqueue("second", createMockBlob("second"));
      });

      expect(result.current.state.isPlaying).toBe(true);

      // Disable TTS
      rerender({ enabled: false });

      expect(result.current.state.currentlyPlaying).toBeNull();
      expect(result.current.state.isPlaying).toBe(false);
      expect(result.current.state.queue).toHaveLength(0);
    });

    it("clears any enqueued items when re-enabled then disabled again", () => {
      const { result, rerender } = renderHook(
        ({ enabled }) => useAudioQueue(enabled),
        { initialProps: { enabled: true } }
      );

      act(() => {
        result.current.enqueue("hello", createMockBlob("hello"));
      });

      expect(result.current.state.currentlyPlaying?.text).toBe("hello");

      // Disable
      rerender({ enabled: false });

      expect(result.current.state.currentlyPlaying).toBeNull();
      expect(result.current.state.queue).toHaveLength(0);

      // Re-enable — state remains clear until new enqueue
      rerender({ enabled: true });

      expect(result.current.state.currentlyPlaying).toBeNull();
      expect(result.current.state.isPlaying).toBe(false);
    });
  });
});
