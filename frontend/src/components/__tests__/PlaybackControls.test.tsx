import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import PlaybackControls from "../dashboard/PlaybackControls";

describe("PlaybackControls", () => {
  const defaultProps = {
    isPlaying: false,
    queueLength: 0,
    onSkip: vi.fn(),
    onStopAll: vi.fn(),
    onPlayNext: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders nothing when not playing and queue is empty", () => {
    const { container } = render(
      <PlaybackControls {...defaultProps} isPlaying={false} queueLength={0} />
    );
    expect(container.firstChild).toBeNull();
  });

  describe("when audio is playing", () => {
    it("shows Skip and Stop All buttons", () => {
      render(
        <PlaybackControls {...defaultProps} isPlaying={true} queueLength={0} />
      );
      expect(
        screen.getByRole("button", { name: /skip current commentary/i })
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /stop all commentary audio/i })
      ).toBeInTheDocument();
    });

    it("does not show Play Next button", () => {
      render(
        <PlaybackControls {...defaultProps} isPlaying={true} queueLength={0} />
      );
      expect(
        screen.queryByRole("button", { name: /play next commentary/i })
      ).not.toBeInTheDocument();
    });
  });

  describe("when not playing but queue has items", () => {
    it("shows Play Next and Stop All buttons", () => {
      render(
        <PlaybackControls {...defaultProps} isPlaying={false} queueLength={3} />
      );
      expect(
        screen.getByRole("button", { name: /play next commentary/i })
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /stop all commentary audio/i })
      ).toBeInTheDocument();
    });

    it("does not show Skip button", () => {
      render(
        <PlaybackControls {...defaultProps} isPlaying={false} queueLength={3} />
      );
      expect(
        screen.queryByRole("button", { name: /skip current commentary/i })
      ).not.toBeInTheDocument();
    });
  });

  describe("click handlers", () => {
    it("calls onSkip when Skip button is clicked", () => {
      const onSkip = vi.fn();
      render(
        <PlaybackControls
          {...defaultProps}
          isPlaying={true}
          queueLength={2}
          onSkip={onSkip}
        />
      );
      fireEvent.click(
        screen.getByRole("button", { name: /skip current commentary/i })
      );
      expect(onSkip).toHaveBeenCalledTimes(1);
    });

    it("calls onStopAll when Stop All button is clicked", () => {
      const onStopAll = vi.fn();
      render(
        <PlaybackControls
          {...defaultProps}
          isPlaying={true}
          queueLength={1}
          onStopAll={onStopAll}
        />
      );
      fireEvent.click(
        screen.getByRole("button", { name: /stop all commentary audio/i })
      );
      expect(onStopAll).toHaveBeenCalledTimes(1);
    });

    it("calls onPlayNext when Play Next button is clicked", () => {
      const onPlayNext = vi.fn();
      render(
        <PlaybackControls
          {...defaultProps}
          isPlaying={false}
          queueLength={2}
          onPlayNext={onPlayNext}
        />
      );
      fireEvent.click(
        screen.getByRole("button", { name: /play next commentary/i })
      );
      expect(onPlayNext).toHaveBeenCalledTimes(1);
    });
  });

  describe("accessible labels", () => {
    it("Skip button has correct aria-label", () => {
      render(
        <PlaybackControls {...defaultProps} isPlaying={true} queueLength={0} />
      );
      const skipBtn = screen.getByRole("button", {
        name: /skip current commentary/i,
      });
      expect(skipBtn).toHaveAttribute("aria-label", "Skip current commentary");
    });

    it("Play Next button has correct aria-label", () => {
      render(
        <PlaybackControls {...defaultProps} isPlaying={false} queueLength={2} />
      );
      const playNextBtn = screen.getByRole("button", {
        name: /play next commentary/i,
      });
      expect(playNextBtn).toHaveAttribute("aria-label", "Play next commentary");
    });

    it("Stop All button has correct aria-label", () => {
      render(
        <PlaybackControls {...defaultProps} isPlaying={true} queueLength={0} />
      );
      const stopBtn = screen.getByRole("button", {
        name: /stop all commentary audio/i,
      });
      expect(stopBtn).toHaveAttribute(
        "aria-label",
        "Stop all commentary audio"
      );
    });
  });
});
