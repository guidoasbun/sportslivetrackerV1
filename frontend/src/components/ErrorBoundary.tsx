"use client";

import React from "react";

interface ErrorBoundaryProps {
  children: React.ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  handleRetry = () => {
    window.location.reload();
  };

  render() {
    if (this.state.hasError) {
      return (
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            minHeight: "300px",
            padding: "48px 24px",
            background: "rgba(30, 41, 59, 0.8)",
            borderRadius: "16px",
            border: "1px solid rgba(255, 255, 255, 0.08)",
            backdropFilter: "blur(12px)",
            textAlign: "center",
          }}
        >
          <h2
            style={{
              fontSize: "22px",
              fontWeight: "bold",
              color: "#f1f5f9",
              marginBottom: "12px",
            }}
          >
            Something went wrong
          </h2>
          <p
            style={{
              color: "#94a3b8",
              fontSize: "14px",
              maxWidth: "480px",
              marginBottom: "24px",
              lineHeight: "1.5",
            }}
          >
            {this.state.error?.message || "An unexpected error occurred while rendering this page."}
          </p>
          <button
            onClick={this.handleRetry}
            style={{
              padding: "10px 24px",
              borderRadius: "8px",
              border: "none",
              background: "linear-gradient(90deg, #6366f1, #4f46e5)",
              color: "white",
              fontSize: "14px",
              fontWeight: 600,
              cursor: "pointer",
              transition: "opacity 0.2s ease",
            }}
          >
            Retry
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
