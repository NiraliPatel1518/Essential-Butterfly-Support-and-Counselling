
import { beforeEach, describe, expect, test } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Intake from "./Intake";
import { mockFetchSequence, renderPage } from "../test/helpers";

const accountDetails = {
  fullName: "Alex Smith",
  email: "alex@mail.com",
  phoneNumber: "4161234567",
};

async function submitWithBackendAnswer(body: unknown, status: number) {
  localStorage.setItem("authToken", "client.jwt");

  mockFetchSequence(
    {
      ok: true,
      body: accountDetails,
    },
    {
      ok: false,
      status,
      body,
    }
  );

  const { container } = renderPage("/intake", <Intake />);

  const user = userEvent.setup();

  // Wait for the account name to load, then replace it.
  const fullNameField = screen.getByLabelText(/client.s full name/i);

  await waitFor(() => {
    expect(fullNameField).toHaveValue("Alex Smith");
  });

  await user.clear(fullNameField);
  await user.type(fullNameField, "Sam Lee");

  await user.selectOptions(
    screen.getByLabelText(/preferred contact method/i),
    "email"
  );

  await user.type(
    screen.getByLabelText(/a little about your situation/i),
    "Some overview"
  );

  await user.type(
    screen.getByLabelText(/what kind of support/i),
    "Some needs"
  );

  await user.click(
    screen.getByRole("button", { name: /submit intake request/i })
  );

  return container;
}

describe("Intake page errors", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  test("shows the message from a JSON answer", async () => {
    await submitWithBackendAnswer(
      { message: "Phone number must be verified." },
      400
    );

    expect(
      await screen.findByText("Phone number must be verified.")
    ).toBeInTheDocument();
  });

  test("shows the error text from a JSON answer", async () => {
    await submitWithBackendAnswer(
      { error: "Service is busy" },
      503
    );

    expect(
      await screen.findByText("Service is busy")
    ).toBeInTheDocument();
  });

  test("shows a plain text answer as it is", async () => {
    await submitWithBackendAnswer("User account not found", 400);

    expect(
      await screen.findByText("User account not found")
    ).toBeInTheDocument();
  });

  test("a second request while one is under review (409) gets a clear message", async () => {
    await submitWithBackendAnswer("", 409);

    expect(
      await screen.findByText(
        /you already have an intake request under review/i
      )
    ).toBeInTheDocument();
  });

  test("a generic Bad Request (400) tells the user to check the form", async () => {
    await submitWithBackendAnswer(
      { error: "Bad Request" },
      400
    );

    expect(
      await screen.findByText(/please review the form and correct/i)
    ).toBeInTheDocument();
  });

  test("an empty 400 answer tells the user to check the form", async () => {
    await submitWithBackendAnswer("", 400);

    expect(
      await screen.findByText(/please review the form and correct/i)
    ).toBeInTheDocument();
  });

  test("a bare 'Error 400' text is not shown to the user", async () => {
    await submitWithBackendAnswer("Error 400", 400);

    expect(
      await screen.findByText(/please review the form and correct/i)
    ).toBeInTheDocument();

    expect(
      screen.queryByText("Error 400")
    ).not.toBeInTheDocument();
  });

  test("any other empty error gets a try-again message", async () => {
    await submitWithBackendAnswer("", 500);

    expect(
      await screen.findByText(
        "Unable to submit the intake form. Please try again."
      )
    ).toBeInTheDocument();
  });
});
