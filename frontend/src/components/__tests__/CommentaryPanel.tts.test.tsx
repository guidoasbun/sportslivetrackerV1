import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, waitFor, act } from "@testing-library/react";
import CommentaryPanel from "../dashboard/CommentaryPanel";

// Mock useTtsPreference
let mockEnabled = false;
const mockToggle = vi.fn();

vi.mock("@/lib/useTtsPreference", () => ({
  useTtsPreference: () => ({ enabled: mockEnabled, toggle: mockToggle }),
}));

// Mock useAudioQueue
const mockEnqueue = vi.fn();
const mockSkip = vi.fn();
const mockStopAll = vi.fn();

vi.mock("@/lib/useAudioQueue", () => ({
  useAudioQueue: () => ({
    enqueue: mockEnqueue,
    skip: mockSkip,
    stopAll: mockStopAll,
    state: { queue: [], currentlyPlaying: null, isPlaying: false },
  }),
}));

// Mock fetchEventSummary
const mockFetchEventSummary = vi.fn();

vi.mock("@/lib/api", () => ({
  fetchEventSummary: (...args: unknown[]) => mockFetchEventSummary(...args),
}));

describe("CommentaryPanel TTS integration", () => {
  beforeEach(() => {
    mockEnabled = false;
    mockToggle.mockClear();
    mockEnqueue.mockClear();
    mockSkip.mockClear();
    mockStopAll.mockClear();
    mockFetchEventSummary.mockReset();
    globalThis.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders TTS toggle in panel header when summary is loaded", async () => {
    mockFetchEventSummary.mockResolvedValue({
      summaryId: "s1",
      eventId: "e1",
      sportType: "SOCCER",
      commentary: "Goal!",
      timestamp: Date.now(),
    });

    render(<CommentaryPanel eventId="e1" />);

    // Wait for summary to load and panel to render with TTS toggle
    await waitFor(() => {
      const button = screen.getByRole("button", { name: /voice commentary/i });
      expect(button).toBeInTheDocument();
      expect(button).toHaveAttribute("aria-pressed");
    });
  });

  it("triggers TTS synthesis when new commentary arrives and TTS is enabled", async () => {
    mockEnabled = true;

    const fakeBlob = new Blob(["audio-data"], { type: "audio/mpeg" });
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      status: 200,
      blob: () => Promise.resolve(fakeBlob),
    });

    mockFetchEventSummary.mockResolvedValue({
      summaryId: "s1",
      eventId: "e1",
      sportType: "SOCCER",
      commentary: "Goal scored by the striker!",
      timestamp: Date.now(),
    });

    render(<CommentaryPanel eventId="e1" />);

    // Wait for the synthesis fetch to be called
    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalledWith(
        expect.stringContaining("/tts/synthesize"),
        expect.objectContaining({
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ text: "Goal scored by the striker!" }),
        })
      );
    });

    // Verify enqueue was called with the commentary text and audio blob
    await waitFor(() => {
      expect(mockEnqueue).toHaveBeenCalledWith(
        "Goal scored by the striker!",
        fakeBlob
      );
    });
  });

  it("does not trigger TTS synthesis when TTS is disabled", async () => {
    mockEnabled = false;

    mockFetchEventSummary.mockResolvedValue({
      summaryId: "s1",
      eventId: "e1",
      sportType: "SOCCER",
      commentary: "Great save!",
      timestamp: Date.now(),
    });

    render(<CommentaryPanel eventId="e1" />);

    // Wait for the component to settle (summary loaded and rendered)
    await waitFor(() => {
      expect(screen.getByText("Great save!")).toBeInTheDocument();
    });

    // Verify fetch was NOT called for TTS synthesis
    expect(globalThis.fetch).not.toHaveBeenCalled();
    expect(mockEnqueue).not.toHaveBeenCalled();
  });
});
