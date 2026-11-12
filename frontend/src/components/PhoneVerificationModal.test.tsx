import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import PhoneVerificationModal from "./PhoneVerificationModal";

/*
 * Phone verification window (client portal). The client enters a phone
 * number, receives a 6-digit text message and types the code.
 *
 * This file has 15 tests:
 *   1  - Closed window shows nothing
 *   2  - Titles: new number / required for intake / update number
 *   3  - Update window shows the current number
 *   4  - A pending number opens the code step directly
 *   5  - Invalid phone number -> error, nothing is sent
 *   6  - Valid number -> code is requested with the client token
 *   7  - Backend error when sending -> error, stays on the phone step
 *   8  - Correct code -> onVerified is called with the number
 *   9  - Wrong code -> error, onVerified is not called
 *   10 - Code shorter than 6 digits -> nothing is sent
 *   11 - Resend the code
 *   12 - Resend too early -> the wait time from the backend is shown
 *   13 - Resend limit reached
 *   14 - "Change number" goes back to the phone step
 *   15 - Close button
 *
 * No text message is ever sent. fetch is replaced with a fake.
 */

type Props = Partial<React.ComponentProps<typeof PhoneVerificationModal>>;

// A fake backend answer. The component reads it with response.text().
function reply(ok: boolean, body: string, status = ok ? 200 : 400) {
  return Promise.resolve({ ok, status, text: () => Promise.resolve(body) });
}

function stubFetch(...answers: Array<Promise<unknown>>) {
  const fetchMock = vi.fn();
  answers.forEach((answer) => fetchMock.mockReturnValueOnce(answer));
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function setup(props: Props = {}) {
  const onClose = vi.fn();
  const onVerified = vi.fn();
  render(
    <PhoneVerificationModal
      isOpen
      phoneVerified={false}
      currentPhoneNumber={null}
      pendingPhoneNumber={null}
      onClose={onClose}
      onVerified={onVerified}
      {...props}
    />
  );
  return { onClose, onVerified, user: userEvent.setup() };
}

// Opens the window directly on the "enter the code" step.
const PENDING = "+14165551234";
const codeStep = (props: Props = {}) => setup({ pendingPhoneNumber: PENDING, ...props });

beforeEach(() => {
  localStorage.setItem("authToken", "client.jwt.token");
});

afterEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
});

describe("PhoneVerificationModal", () => {
  test("shows nothing when it is closed", () => {
    stubFetch();
    setup({ isOpen: false });

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  test("shows the right title for each situation", () => {
    stubFetch();
    const { unmount } = render(
      <PhoneVerificationModal
        isOpen
        phoneVerified={false}
        currentPhoneNumber={null}
        pendingPhoneNumber={null}
        onClose={vi.fn()}
        onVerified={vi.fn()}
      />
    );
    expect(screen.getByRole("heading")).toHaveTextContent("Verify your phone number");
    unmount();

    const required = render(
      <PhoneVerificationModal
        isOpen
        requiredForIntake
        phoneVerified={false}
        currentPhoneNumber={null}
        pendingPhoneNumber={null}
        onClose={vi.fn()}
        onVerified={vi.fn()}
      />
    );
    expect(screen.getByRole("heading")).toHaveTextContent("Verify your phone number to proceed");
    required.unmount();

    render(
      <PhoneVerificationModal
        isOpen
        phoneVerified
        currentPhoneNumber={PENDING}
        pendingPhoneNumber={null}
        onClose={vi.fn()}
        onVerified={vi.fn()}
      />
    );
    expect(screen.getByRole("heading")).toHaveTextContent("Update your phone number");
  });

  test("shows the current number when the client updates it", () => {
    stubFetch();
    setup({ phoneVerified: true, currentPhoneNumber: PENDING });

    expect(screen.getByLabelText("Phone number")).toHaveValue(PENDING);
  });

  test("opens on the code step when a number is pending", () => {
    stubFetch();
    codeStep();

    expect(screen.getByText(PENDING)).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(/continue with the verification code/i);
    expect(screen.getByLabelText("6-digit verification code")).toBeInTheDocument();
  });

  test("refuses an invalid phone number and sends nothing", async () => {
    const fetchMock = stubFetch();
    const { user } = setup();

    await user.type(screen.getByLabelText("Phone number"), "4165551234");
    await user.click(screen.getByRole("button", { name: "Send verification code" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/international format/i);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  test("requests a code for the cleaned-up number with the client token", async () => {
    const fetchMock = stubFetch(reply(true, "A verification code has been sent."));
    const { user } = setup();

    await user.type(screen.getByLabelText("Phone number"), "+1 (416) 555-1234");
    await user.click(screen.getByRole("button", { name: "Send verification code" }));

    expect(await screen.findByLabelText("6-digit verification code")).toBeInTheDocument();
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/phone-verification/send");
    expect(JSON.parse(init.body)).toEqual({ phoneNumber: "+14165551234" });
    expect(init.headers.Authorization).toBe("Bearer client.jwt.token");
    expect(screen.getByText("+14165551234")).toBeInTheDocument();
    // the first wait is 60 seconds, so there is no resend button yet
    expect(screen.getByText("Resend code in 60s")).toBeInTheDocument();
  });

  test("shows the backend error and stays on the phone step", async () => {
    stubFetch(reply(false, "This phone number is already verified."));
    const { user } = setup();

    await user.type(screen.getByLabelText("Phone number"), "+14165551234");
    await user.click(screen.getByRole("button", { name: "Send verification code" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("already verified");
    expect(screen.getByLabelText("Phone number")).toBeInTheDocument();
  });

  test("calls onVerified with the number after the correct code", async () => {
    const fetchMock = stubFetch(reply(true, "Phone number verified successfully."));
    const { user, onVerified } = codeStep();

    await user.type(screen.getByLabelText("6-digit verification code"), "123456");
    await user.click(screen.getByRole("button", { name: "Verify phone number" }));

    await vi.waitFor(() => expect(onVerified).toHaveBeenCalledWith(PENDING));
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/phone-verification/verify");
    expect(JSON.parse(init.body)).toEqual({ code: "123456" });
    expect(await screen.findByRole("status")).toHaveTextContent(/verified successfully/i);
  });

  test("shows the error and does not call onVerified when the code is wrong", async () => {
    stubFetch(reply(false, JSON.stringify({ message: "The verification code is incorrect. 4 attempt(s) remaining." })));
    const { user, onVerified } = codeStep();

    await user.type(screen.getByLabelText("6-digit verification code"), "000000");
    await user.click(screen.getByRole("button", { name: "Verify phone number" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("4 attempt(s) remaining");
    expect(onVerified).not.toHaveBeenCalled();
  });

  test("does not send a code shorter than 6 digits", async () => {
    const fetchMock = stubFetch();
    const { user, onVerified } = codeStep();

    await user.type(screen.getByLabelText("6-digit verification code"), "123");
    await user.click(screen.getByRole("button", { name: "Verify phone number" }));

    expect(fetchMock).not.toHaveBeenCalled();
    expect(onVerified).not.toHaveBeenCalled();
  });

  test("resends the code and makes the next wait longer", async () => {
    const fetchMock = stubFetch(reply(true, "A new verification code has been sent."));
    const { user } = codeStep();

    await user.click(screen.getByRole("button", { name: "Resend code" }));

    expect(await screen.findByText("Resend code in 90s")).toBeInTheDocument();
    expect(fetchMock.mock.calls[0][0]).toBe("http://localhost:8080/api/phone-verification/resend");
    expect(screen.getByRole("status")).toHaveTextContent("A new verification code has been sent.");
  });

  test("shows the wait time sent by the backend when resending too early", async () => {
    stubFetch(reply(false, "Please wait 25 second(s) before requesting another code."));
    const { user } = codeStep();

    await user.click(screen.getByRole("button", { name: "Resend code" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Please wait 25 second(s)");
    expect(screen.getByText("Resend code in 25s")).toBeInTheDocument();
  });

  test("stops offering resend when the limit is reached", async () => {
    stubFetch(reply(false, "You have reached the maximum number of resend requests."));
    const { user } = codeStep();

    await user.click(screen.getByRole("button", { name: "Resend code" }));

    expect(
      await screen.findByText("You have reached the maximum of 3 resend requests.")
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Resend code" })).not.toBeInTheDocument();
  });

  test("goes back to the phone step with Change number", async () => {
    stubFetch();
    const { user } = codeStep();

    await user.click(screen.getByRole("button", { name: "Change number" }));

    expect(screen.getByLabelText("Phone number")).toBeInTheDocument();
    expect(screen.queryByLabelText("6-digit verification code")).not.toBeInTheDocument();
  });

  test("calls onClose with the close button", async () => {
    stubFetch();
    const { user, onClose } = setup();

    await user.click(screen.getByRole("button", { name: "Close phone verification" }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
