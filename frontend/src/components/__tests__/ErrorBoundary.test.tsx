import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import ErrorBoundary from "../ErrorBoundary";

// A component that throws on render
function ThrowingComponent({ error }: { error: Error }) {
  throw error;
}

function GoodComponent() {
  return <p>All is well</p>;
}

describe("ErrorBoundary", () => {
  // Suppress React error boundary console.error noise in test output
  const originalConsoleError = console.error;
  beforeEach(() => {
    console.error = vi.fn();
  });
  afterEach(() => {
    console.error = originalConsoleError;
  });

  it("renders children when no error occurs", () => {
    render(
      <ErrorBoundary>
        <GoodComponent />
      </ErrorBoundary>
    );

    expect(screen.getByText("All is well")).toBeInTheDocument();
  });

  it("displays fallback UI with error description when a child throws", () => {
    render(
      <ErrorBoundary>
        <ThrowingComponent error={new Error("Test render failure")} />
      </ErrorBoundary>
    );

    expect(screen.getByText("Something went wrong")).toBeInTheDocument();
    expect(screen.getByText("Test render failure")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /retry/i })).toBeInTheDocument();
  });

  it("calls window.location.reload when the Retry button is clicked", () => {
    const reloadMock = vi.fn();
    Object.defineProperty(window, "location", {
      value: { reload: reloadMock },
      writable: true,
    });

    render(
      <ErrorBoundary>
        <ThrowingComponent error={new Error("Crash")} />
      </ErrorBoundary>
    );

    fireEvent.click(screen.getByRole("button", { name: /retry/i }));
    expect(reloadMock).toHaveBeenCalledTimes(1);
  });

  it("shows a generic message when error has no message", () => {
    render(
      <ErrorBoundary>
        <ThrowingComponent error={new Error("")} />
      </ErrorBoundary>
    );

    expect(
      screen.getByText("An unexpected error occurred while rendering this page.")
    ).toBeInTheDocument();
  });
});
