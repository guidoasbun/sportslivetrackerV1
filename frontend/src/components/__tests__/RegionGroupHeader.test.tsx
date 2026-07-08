import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import RegionGroupHeader from "../dashboard/RegionGroupHeader";

describe("RegionGroupHeader", () => {
  const defaultProps = {
    label: "England - Premier League",
    fixtureCount: 3,
    isExpanded: false,
    onToggle: vi.fn(),
    groupId: "group-england-premier-league",
  };

  it("renders group label and fixture count when collapsed", () => {
    render(<RegionGroupHeader {...defaultProps} isExpanded={false} />);

    expect(screen.getByText("England - Premier League")).toBeInTheDocument();
    expect(screen.getByText("(3)")).toBeInTheDocument();
  });

  it("does not show fixture count when expanded", () => {
    render(<RegionGroupHeader {...defaultProps} isExpanded={true} />);

    expect(screen.getByText("England - Premier League")).toBeInTheDocument();
    expect(screen.queryByText("(3)")).not.toBeInTheDocument();
  });

  it("calls onToggle when clicked", () => {
    const onToggle = vi.fn();
    render(<RegionGroupHeader {...defaultProps} onToggle={onToggle} />);

    const button = screen.getByRole("button");
    fireEvent.click(button);

    expect(onToggle).toHaveBeenCalledTimes(1);
  });

  it("calls onToggle when Enter key is pressed", () => {
    const onToggle = vi.fn();
    render(<RegionGroupHeader {...defaultProps} onToggle={onToggle} />);

    const button = screen.getByRole("button");
    fireEvent.keyDown(button, { key: "Enter" });

    expect(onToggle).toHaveBeenCalledTimes(1);
  });

  it("calls onToggle when Space key is pressed", () => {
    const onToggle = vi.fn();
    render(<RegionGroupHeader {...defaultProps} onToggle={onToggle} />);

    const button = screen.getByRole("button");
    fireEvent.keyDown(button, { key: " " });

    expect(onToggle).toHaveBeenCalledTimes(1);
  });

  it("does not call onToggle for other keys", () => {
    const onToggle = vi.fn();
    render(<RegionGroupHeader {...defaultProps} onToggle={onToggle} />);

    const button = screen.getByRole("button");
    fireEvent.keyDown(button, { key: "Tab" });
    fireEvent.keyDown(button, { key: "Escape" });

    expect(onToggle).not.toHaveBeenCalled();
  });

  it("has aria-expanded=false when collapsed", () => {
    render(<RegionGroupHeader {...defaultProps} isExpanded={false} />);

    const button = screen.getByRole("button");
    expect(button).toHaveAttribute("aria-expanded", "false");
  });

  it("has aria-expanded=true when expanded", () => {
    render(<RegionGroupHeader {...defaultProps} isExpanded={true} />);

    const button = screen.getByRole("button");
    expect(button).toHaveAttribute("aria-expanded", "true");
  });

  it("has aria-controls set to the groupId", () => {
    render(<RegionGroupHeader {...defaultProps} />);

    const button = screen.getByRole("button");
    expect(button).toHaveAttribute("aria-controls", "group-england-premier-league");
  });

  it("has tabIndex=0 for keyboard accessibility", () => {
    render(<RegionGroupHeader {...defaultProps} />);

    const button = screen.getByRole("button");
    expect(button).toHaveAttribute("tabindex", "0");
  });

  it("rotates chevron icon when expanded", () => {
    const { container, rerender } = render(
      <RegionGroupHeader {...defaultProps} isExpanded={false} />
    );

    const svgCollapsed = container.querySelector("svg") as SVGElement;
    expect(svgCollapsed.style.transform).toBe("rotate(0deg)");

    rerender(<RegionGroupHeader {...defaultProps} isExpanded={true} />);

    const svgExpanded = container.querySelector("svg") as SVGElement;
    expect(svgExpanded.style.transform).toBe("rotate(90deg)");
  });
});
