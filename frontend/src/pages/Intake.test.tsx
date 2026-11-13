
import { beforeEach, describe, expect, test } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Intake from "./Intake";
import {
  mockFetch,
  mockFetchSequence,
  renderPage,
} from "../test/helpers";

const accountDetails = {
  fullName: "Alex Smith",
  email: "alex@mail.com",
  phoneNumber: "4161234567",
};

async function fillRequiredFields() {
  const user = userEvent.setup();

  // Wait for the account name to load before replacing it.
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

  return user;
}

const submitButton = () =>
  screen.getByRole("button", { name: /submit intake request/i });

describe("Intake page", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  test("redirects user to login when there is no token", async () => {
    const fetchMock = mockFetch(true);
    renderPage("/intake", <Intake />);

    const user = userEvent.setup();

    await user.type(
      screen.getByLabelText(/client.s full name/i),
      "Sam Lee"
    );

    await user.selectOptions(
      screen.getByLabelText(/preferred contact method/i),
      "phone"
    );

    await user.type(
      screen.getByLabelText(/a little about your situation/i),
      "Some overview"
    );

    await user.type(
      screen.getByLabelText(/what kind of support/i),
      "Some needs"
    );

    await user.click(submitButton());

    expect(await screen.findByText(/landed on/i)).toHaveTextContent(
      "/login"
    );

    expect(fetchMock).not.toHaveBeenCalled();
  });

  test("sends form with Bearer token and clears form on success", async () => {
    localStorage.setItem("authToken", "client.jwt");

    const fetchMock = mockFetchSequence(
      {
        ok: true,
        body: accountDetails,
      },
      {
        ok: true,
        body: "Intake submission received successfully",
      }
    );

    renderPage("/intake", <Intake />);

    const user = await fillRequiredFields();
    await user.click(submitButton());

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    const intakeCall = fetchMock.mock.calls.find(([url]) =>
      String(url).includes("/api/intake")
    );

    expect(intakeCall).toBeDefined();

    const [url, options] = intakeCall!;

    expect(url).toBe("http://localhost:8080/api/intake");
    expect(
      new Headers(options?.headers).get("Authorization")
    ).toBe("Bearer client.jwt");
    
    const body = JSON.parse(String(options?.body));

    expect(body.clientFullName).toBe("Sam Lee");
    expect(body.contactInfo).toBe("alex@mail.com");
    expect(body.preferredContactMethod).toBe("email");
    expect(body.overview).toBe("Some overview");
    expect(body.supportNeeds).toBe("Some needs");

    expect(
      await screen.findByText(/submitted successfully/i)
    ).toBeInTheDocument();

    // The form resets to the account's default name.
    expect(
      screen.getByLabelText(/client.s full name/i)
    ).toHaveValue("Alex Smith");

    expect(
      screen.getByLabelText(/a little about your situation/i)
    ).toHaveValue("");
  });

  test("shows backend error message", async () => {
    localStorage.setItem("authToken", "client.jwt");

    mockFetchSequence(
      {
        ok: true,
        body: accountDetails,
      },
      {
        ok: false,
        status: 400,
        body: "User account not found",
      }
    );

    renderPage("/intake", <Intake />);

    const user = await fillRequiredFields();
    await user.click(submitButton());

    expect(
      await screen.findByText("User account not found")
    ).toBeInTheDocument();
  });

  test("does not submit when required fields are empty", async () => {
    localStorage.setItem("authToken", "client.jwt");

    const fetchMock = mockFetchSequence({
      ok: true,
      body: accountDetails,
    });

    renderPage("/intake", <Intake />);

    await userEvent.click(submitButton());

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    expect(
      fetchMock.mock.calls.some(([url]) =>
        String(url).includes("/api/intake")
      )
    ).toBe(false);
  });
});
