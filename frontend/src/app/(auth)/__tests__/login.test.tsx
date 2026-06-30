import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import LoginPage from "../login/page";

const mockPush = vi.fn();
const mockRefresh = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, refresh: mockRefresh }),
  useSearchParams: () => new URLSearchParams(),
}));

function getEmailInput() {
  return document.querySelector('input[type="email"]') as HTMLInputElement;
}

function getPasswordInput() {
  return document.querySelector('input[type="password"]') as HTMLInputElement;
}

describe("LoginPage", () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    vi.resetAllMocks();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it("calls /api/auth/email/signin with entered email and password on submit", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ token: "abc" }),
    });
    global.fetch = fetchMock;

    render(<LoginPage />);

    fireEvent.change(getEmailInput(), {
      target: { value: "user@test.com" },
    });
    fireEvent.change(getPasswordInput(), {
      target: { value: "Secret123!" },
    });
    fireEvent.click(screen.getByRole("button", { name: /sign in/i }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith("/api/auth/email/signin", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: "user@test.com", password: "Secret123!" }),
      });
    });
  });

  it("renders error message when API response is not ok", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      json: () => Promise.resolve({ error: "Invalid credentials" }),
    });

    render(<LoginPage />);

    fireEvent.change(getEmailInput(), {
      target: { value: "user@test.com" },
    });
    fireEvent.change(getPasswordInput(), {
      target: { value: "wrong" },
    });
    fireEvent.click(screen.getByRole("button", { name: /sign in/i }));

    await waitFor(() => {
      expect(screen.getByText("Invalid credentials")).toBeInTheDocument();
    });
  });

  it("disables the submit button while submission is in progress", async () => {
    let resolveFetch!: (value: unknown) => void;
    global.fetch = vi.fn().mockImplementation(
      () => new Promise((resolve) => { resolveFetch = resolve; })
    );

    render(<LoginPage />);

    fireEvent.change(getEmailInput(), {
      target: { value: "user@test.com" },
    });
    fireEvent.change(getPasswordInput(), {
      target: { value: "Secret123!" },
    });
    fireEvent.click(screen.getByRole("button", { name: /sign in/i }));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /signing in/i })).toBeDisabled();
    });

    // Resolve the fetch to clean up
    resolveFetch({ ok: true, json: () => Promise.resolve({}) });

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /sign in/i })).not.toBeDisabled();
    });
  });
});
