
import { beforeEach, describe, expect, test } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Login from "./Login";
import Signup from "./Signup";
import Contact from "./Contact";
import Intake from "./Intake";
import {
  mockFetch,
  mockFetchSequence,
  renderPage,
} from "../test/helpers";

const EMAIL_ERROR =
  "Please enter a valid email address such as name@example.com.";

const accountDetails = {
  fullName: "Alex Smith",
  email: "alex@mail.com",
  phoneNumber: "4165551234",
};

describe("Email format", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  test("client login rejects an email without an ending", async () => {
    const fetchMock = mockFetch(true, "token");
    renderPage("/login", <Login />);

    const user = userEvent.setup();

    await user.type(screen.getByLabelText(/email/i), "jane@mail");
    await user.type(
      screen.getByLabelText(/^password$/i),
      "Password123"
    );
    await user.click(
      screen.getByRole("button", { name: "Log In" })
    );

    expect(await screen.findByText(EMAIL_ERROR)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  test("signup rejects an email without an ending", async () => {
    const fetchMock = mockFetch(true);
    renderPage("/signup", <Signup />);

    const user = userEvent.setup();

    await user.type(screen.getByLabelText("Full name"), "Jane Doe");
    await user.type(
      screen.getByLabelText("Email address"),
      "jane@mail"
    );
    await user.type(
      screen.getByLabelText("Create a password"),
      "Password123"
    );
    await user.type(
      screen.getByLabelText("Confirm password"),
      "Password123"
    );
    await user.click(screen.getByRole("checkbox"));
    await user.click(
      screen.getByRole("button", { name: "Create Account" })
    );

    expect(fetchMock).not.toHaveBeenCalled();
  });

  test("contact rejects an email without an ending", async () => {
    const fetchMock = mockFetch(true);
    renderPage("/contact", <Contact />);

    const user = userEvent.setup();

    await user.type(screen.getByLabelText(/full name/i), "Jane Doe");
    await user.type(
      screen.getByLabelText(/email address/i),
      "jane@mail"
    );
    await user.selectOptions(
      screen.getByLabelText(/subject/i),
      "services"
    );
    await user.type(screen.getByLabelText(/message/i), "Hello");
    await user.click(
      screen.getByRole("button", { name: "Send Message" })
    );

    expect(fetchMock).not.toHaveBeenCalled();
  });

  test("intake rejects an invalid account email", async () => {
    localStorage.setItem("authToken", "client.jwt");

    const fetchMock = mockFetchSequence({
      ok: true,
      body: {
        ...accountDetails,
        email: "alex@mail",
      },
    });

    renderPage("/intake", <Intake />);

    const user = userEvent.setup();

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

    expect(await screen.findByText(EMAIL_ERROR)).toBeInTheDocument();

    expect(
      fetchMock.mock.calls.some(([url]) =>
        String(url).includes("/api/intake")
      )
    ).toBe(false);
  });

  test("client login accepts a normal email", async () => {
    const fetchMock = mockFetch(true, "client.jwt");
    renderPage("/login", <Login />);

    const user = userEvent.setup();

    await user.type(
      screen.getByLabelText(/email/i),
      "jane.doe@mail.com"
    );
    await user.type(
      screen.getByLabelText(/^password$/i),
      "Password123"
    );
    await user.click(
      screen.getByRole("button", { name: "Log In" })
    );

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(screen.queryByText(EMAIL_ERROR)).not.toBeInTheDocument();
  });
});

describe("Intake phone number", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  async function openWithPhone() {
    localStorage.setItem("authToken", "client.jwt");

    const fetchMock = mockFetchSequence({
      ok: true,
      body: accountDetails,
    });

    renderPage("/intake", <Intake />);

    const user = userEvent.setup();

    await user.selectOptions(
      screen.getByLabelText(/preferred contact method/i),
      "phone"
    );

    return { user, fetchMock };
  }

  test("phone number is loaded from the authenticated account", async () => {
    await openWithPhone();

    const phoneField = await screen.findByLabelText(/^phone number/i);

    expect(phoneField).toHaveValue("4165551234");
    expect(phoneField).toHaveAttribute("readonly");
  });

  test("phone number cannot be edited manually", async () => {
    const { user } = await openWithPhone();

    const phoneField = await screen.findByLabelText(/^phone number/i);

    expect(phoneField).toHaveValue("4165551234");
    expect(phoneField).toHaveAttribute("readonly");

    await user.click(phoneField);

    expect(phoneField).toHaveValue("4165551234");
  });
});

describe("Intake under review", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  test("shows message when an intake is already under review", async () => {
    localStorage.setItem("authToken", "client.jwt");

    const message =
      "You already have an intake request under review. " +
      "Please wait for it to be reviewed before submitting another request.";

    mockFetchSequence(
      {
        ok: true,
        body: accountDetails,
      },
      {
        ok: false,
        status: 409,
        body: message,
      }
    );

    renderPage("/intake", <Intake />);

    const user = userEvent.setup();

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

    expect(await screen.findByText(message)).toBeInTheDocument();
  });
});
