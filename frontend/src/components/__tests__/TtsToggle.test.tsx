import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import TtsToggle from "../dashboard/TtsToggle";

// Mock the useTtsPreference hook
const mockToggle = vi.fn();
let mockEnabled = false;

vi.mock("@/lib/useTtsPreference", () => ({
  useTtsPreference: () => ({ enabled: mockEnabled, toggle: mockToggle }),
}));

describe("TtsToggle", () => {
  beforeEach(() => {
    mockEnabled = false;
    mockToggle.mockClear();
  });

  it("renders with aria-pressed='false' when disabled", () => {
    mockEnabled = false;
    render(<TtsToggle />);

    const button = screen.getByRole("button");
    expect(button).toHaveAttribute("aria-pressed", "false");
  });

  it("renders with aria-pressed='true' when enabled", () => {
    mockEnabled = true;
    render(<TtsToggle />);

    const button = screen.getByRole("button");
    expect(button).toHaveAttribute("aria-pressed", "true");
  });

  it("calls toggle on click", () => {
    mockEnabled = false;
    render(<TtsToggle />);

    const button = screen.getByRole("button");
    fireEvent.click(button);

    expect(mockToggle).toHaveBeenCalledTimes(1);
  });

  it("shows 'Enable voice commentary' tooltip when disabled", () => {
    mockEnabled = false;
    render(<TtsToggle />);

    const button = screen.getByRole("button");
    expect(button).toHaveAttribute("title", "Enable voice commentary");
  });

  it("shows 'Disable voice commentary' tooltip when enabled", () => {
    mockEnabled = true;
    render(<TtsToggle />);

    const button = screen.getByRole("button");
    expect(button).toHaveAttribute("title", "Disable voice commentary");
  });
});
