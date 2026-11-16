import { Component, type ErrorInfo, type ReactNode } from "react";
import { Link } from "react-router-dom";
import "./ErrorStates.css";

type Props = { children: ReactNode };
type State = { failed: boolean };

export default class AppErrorBoundary extends Component<Props, State> {
  state: State = { failed: false };

  static getDerivedStateFromError(): State {
    return { failed: true };
  }

  componentDidCatch(error: Error, details: ErrorInfo) {
    console.error("Unexpected page error", error, details);
  }

  render() {
    if (!this.state.failed) return this.props.children;

    return (
      <main className="friendly-error-page" id="main-content" tabIndex={-1}>
        <img
          src="/images/butterfly-mark.png"
          alt=""
          className="friendly-error-mark"
        />
        <p className="friendly-error-eyebrow">WE'RE HERE TO HELP</p>
        <h1>This page needs a moment</h1>
        <p>
          Something unexpected happened, but your information has not been
          submitted again. Please refresh the page or return home.
        </p>
        <div className="friendly-error-actions">
          <button type="button" onClick={() => window.location.reload()}>
            Refresh page
          </button>
          <Link to="/">Return home</Link>
        </div>
      </main>
    );
  }
}

