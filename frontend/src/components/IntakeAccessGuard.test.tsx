import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import IntakeAccessGuard from "./IntakeAccessGuard";
import { mockFetch, mockFetchNetworkError, renderPage } from "../test/helpers";

type ModalProps = {
  isOpen: boolean;
  phoneVerified: boolean;
  currentPhoneNumber: string | null;
  pendingPhoneNumber: string | null;
  requiredForIntake?: boolean;
  onClose: () => void;
  onVerified: (phoneNumber: string) => void;
};

// The real Intake form and phone modal have their own tests; here we only
// check what the guard decides and what it passes on.
vi.mock("../pages/Intake", () => ({
  default: () => <p>Intake form</p>,
}));

vi.mock("./PhoneVerificationModal", () => ({
  default: (props: ModalProps) => (
    <div>
      <p>Phone modal open={String(props.isOpen)} required={String(props.requiredForIntake)}</p>
      <p>Current {props.currentPhoneNumber ?? "none"}</p>
      <p>Pending {props.pendingPhoneNumber ?? "none"}</p>
      <button type="button" onClick={props.onClose}>Close modal</button>
      <button type="button" onClick={() => props.onVerified("+14165550123")}>Finish verification</button>
    </div>
  ),
}));

function renderGuard() {
  return renderPage("/intake", <IntakeAccessGuard />);
}

beforeEach(() => {
  localStorage.setItem("authToken", "client-token");
});

describe("IntakeAccessGuard", () => {
  it("sends a visitor without a token to the login page", async () => {
    localStorage.removeItem("authToken");
    const fetchMock = mockFetch(true, "{}");

    renderGuard();

    expect(await screen.findByText("Landed on /login")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("shows a checking message first", () => {
    mockFetch(true, JSON.stringify({ phoneVerified: true, phoneNumber: null, pendingPhoneNumber: null }));

    renderGuard();

    expect(screen.getByRole("status")).toHaveTextContent("Checking your phone verification status...");
  });

  it("asks the server with the client's token", async () => {
    const fetchMock = mockFetch(true, JSON.stringify({ phoneVerified: true, phoneNumber: "+1", pendingPhoneNumber: null }));

    renderGuard();
    await screen.findByText("Intake form");

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toMatch(/\/api\/phone-verification\/status$/);
    expect(JSON.stringify((init as RequestInit).headers)).toContain("Bearer client-token");
  });

  it("opens the Intake form for a client with a verified phone", async () => {
    mockFetch(true, JSON.stringify({ phoneVerified: true, phoneNumber: "+14165550123", pendingPhoneNumber: null }));

    renderGuard();

    expect(await screen.findByText("Intake form")).toBeInTheDocument();
    expect(screen.queryByText("Phone verification required")).not.toBeInTheDocument();
  });

  it("asks an unverified client to verify, and does not show the form", async () => {
    mockFetch(true, JSON.stringify({ phoneVerified: false, phoneNumber: "+14165550123", pendingPhoneNumber: "+14165550199" }));

    renderGuard();

    expect(await screen.findByText("Phone verification required")).toBeInTheDocument();
    expect(screen.queryByText("Intake form")).not.toBeInTheDocument();
    expect(screen.getByText("Phone modal open=true required=true")).toBeInTheDocument();
    expect(screen.getByText("Current +14165550123")).toBeInTheDocument();
    expect(screen.getByText("Pending +14165550199")).toBeInTheDocument();
  });

  it("passes no numbers on when the client has none", async () => {
    mockFetch(true, JSON.stringify({ phoneVerified: false, phoneNumber: null, pendingPhoneNumber: null }));

    renderGuard();

    expect(await screen.findByText("Current none")).toBeInTheDocument();
    expect(screen.getByText("Pending none")).toBeInTheDocument();
  });

  it("opens the Intake form as soon as the phone is verified", async () => {
    mockFetch(true, JSON.stringify({ phoneVerified: false, phoneNumber: null, pendingPhoneNumber: null }));
    const user = userEvent.setup();

    renderGuard();
    await user.click(await screen.findByRole("button", { name: "Finish verification" }));

    expect(await screen.findByText("Intake form")).toBeInTheDocument();
    expect(screen.queryByText("Phone verification required")).not.toBeInTheDocument();
  });

  it("sends the client back to the dashboard when the modal is closed", async () => {
    mockFetch(true, JSON.stringify({ phoneVerified: false, phoneNumber: null, pendingPhoneNumber: null }));
    const user = userEvent.setup();

    renderGuard();
    await user.click(await screen.findByRole("button", { name: "Close modal" }));

    expect(await screen.findByText("Landed on /client-dashboard")).toBeInTheDocument();
  });

  it.each([401, 403])("signs the client out and goes to login when the server says %i", async (status) => {
    mockFetch(false, "");
    vi.mocked(fetch).mockResolvedValue({ ok: false, status, text: async () => "", json: async () => ({}) } as Response);

    renderGuard();

    expect(await screen.findByText("Landed on /login")).toBeInTheDocument();
    expect(localStorage.getItem("authToken")).toBeNull();
  });

  it("shows an error with a retry button when the status cannot be checked", async () => {
    mockFetch(false, "");
    vi.mocked(fetch).mockResolvedValue({ ok: false, status: 500, text: async () => "", json: async () => ({}) } as Response);

    renderGuard();

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Unable to access Intake");
    expect(alert).toHaveTextContent("Unable to verify your phone verification status. Please try again.");
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
    expect(screen.queryByText("Intake form")).not.toBeInTheDocument();
  });

  it("shows an error when the server cannot be reached", async () => {
    mockFetchNetworkError();

    renderGuard();

    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Failed to fetch"));
    expect(screen.queryByText("Intake form")).not.toBeInTheDocument();
  });
});
