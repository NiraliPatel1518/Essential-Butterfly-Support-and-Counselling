import { describe, expect, test } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Login from "./Login";
import AdminLogin from "./AdminLogin";
import Signup from "./Signup";
import Contact from "./Contact";
import Intake from "./Intake";
import { mockFetch, renderPage } from "../test/helpers";

/*
 * UC-01 / UC-02 / UC-03: Email and phone format checks on the forms
 * An email must look like name@example.com (it needs a dot and an ending like .com).
 *
 * This file has 9 tests:
 *
 *   Email format (6 tests)
 *     Test 1 - Client Login: "jane@mail" -> clear error, nothing is sent
 *     Test 2 - Admin Login: "admin@test" -> clear error, nothing is sent
 *     Test 3 - Signup: "jane@mail" -> account is not created
 *     Test 4 - Contact: "jane@mail" -> message is not sent
 *     Test 5 - Intake (contact by email): "alex@mail" -> form is not sent
 *     Test 6 - Client Login: correct email -> request is sent
 *
 *   Intake phone number (2 tests)
 *     Test 7 - Phone field keeps only numbers (letters and symbols are removed)
 *     Test 8 - Phone field stops at 10 digits
 *
 *   Intake under review (1 test)
 *     Test 9 - Second intake while the first is under review -> message is shown
 *
 * The backend is NOT needed. mockFetch() creates a fake backend response.
 */

const EMAIL_ERROR = "Please enter a valid email address such as name@example.com.";

describe("Email format", () => {
  /*
   * TEST 1: Client Login with an incomplete email
   * Checks that:
   *   - The message "Please enter a valid email address such as name@example.com." is shown
   *   - No request is sent to the backend
   */
  test("client login rejects an email without an ending", async () => {
    // Arrange: open the Login page
    const fetchMock = mockFetch(true, "token");
    renderPage("/login", <Login />);

    // Act: type "jane@mail" and click Log In
    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/email/i), "jane@mail");
    await user.type(screen.getByLabelText(/^password$/i), "Password123");
    await user.click(screen.getByRole("button", { name: "Log In" }));

    // Assert: error is shown and backend is not called
    expect(await screen.findByText(EMAIL_ERROR)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  /*
   * TEST 2: Admin Login with an incomplete email
   * Checks that:
   *   - The same clear error is shown on the admin page
   *   - No request is sent to the backend
   */
  test("admin login rejects an email without an ending", async () => {
    // Arrange: open the Admin Login page
    const fetchMock = mockFetch(true, "token");
    renderPage("/admin-login", <AdminLogin />);

    // Act: type "admin@test" and click Log In
    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/email/i), "admin@test");
    await user.type(screen.getByLabelText(/^password$/i), "Admin@12345");
    await user.click(screen.getByRole("button", { name: "Log In" }));

    // Assert: error is shown and backend is not called
    expect(await screen.findByText(EMAIL_ERROR)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  /*
   * TEST 3: Signup with an incomplete email
   * Checks that:
   *   - The account is not created (no request is sent)
   */
  test("signup rejects an email without an ending", async () => {
    // Arrange: open the Signup page
    const fetchMock = mockFetch(true);
    renderPage("/signup", <Signup />);

    // Act: fill the form with "jane@mail" and click Create Account
    const user = userEvent.setup();
    await user.type(screen.getByLabelText("Full name"), "Jane Doe");
    await user.type(screen.getByLabelText("Email address"), "jane@mail");
    await user.type(screen.getByLabelText("Create a password"), "Password123");
    await user.type(screen.getByLabelText("Confirm password"), "Password123");
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Create Account" }));

    // Assert: backend is not called
    expect(fetchMock).not.toHaveBeenCalled();
  });

  /*
   * TEST 4: Contact message with an incomplete email
   * Checks that:
   *   - The message is not sent
   */
  test("contact rejects an email without an ending", async () => {
    // Arrange: open the Contact page
    const fetchMock = mockFetch(true);
    renderPage("/contact", <Contact />);

    // Act: fill the form with "jane@mail" and click Send Message
    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/full name/i), "Jane Doe");
    await user.type(screen.getByLabelText(/email address/i), "jane@mail");
    await user.selectOptions(screen.getByLabelText(/subject/i), "services");
    await user.type(screen.getByLabelText(/message/i), "Hello");
    await user.click(screen.getByRole("button", { name: "Send Message" }));

    // Assert: backend is not called
    expect(fetchMock).not.toHaveBeenCalled();
  });

  /*
   * TEST 5: Intake with an incomplete email
   * Checks that:
   *   - When "Email" is the contact method, "alex@mail" is not accepted
   *   - The intake form is not sent
   */
  test("intake rejects an email without an ending", async () => {
    // Arrange: user is logged in, open the Intake page
    localStorage.setItem("authToken", "client.jwt");
    const fetchMock = mockFetch(true);
    renderPage("/intake", <Intake />);

    // Act: choose Email and type "alex@mail", fill the rest, submit
    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/client's full name/i), "Sam Lee");
    await user.selectOptions(screen.getByLabelText(/preferred contact method/i), "email");
    await user.type(screen.getByLabelText(/contact information/i), "alex@mail");
    await user.type(screen.getByLabelText(/^overview/i), "Some overview");
    await user.type(screen.getByLabelText(/support needs/i), "Some needs");
    await user.click(screen.getByRole("button", { name: "Continue to Secure Intake" }));

    // Assert: backend is not called
    expect(fetchMock).not.toHaveBeenCalled();
  });

  /*
   * TEST 6: Client Login with a correct email
   * Checks that:
   *   - A normal email is accepted and the login request is sent
   */
  test("client login accepts a normal email", async () => {
    // Arrange: open the Login page
    const fetchMock = mockFetch(true, "client.jwt");
    renderPage("/login", <Login />);

    // Act: type a correct email and click Log In
    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/email/i), "jane.doe@mail.com");
    await user.type(screen.getByLabelText(/^password$/i), "Password123");
    await user.click(screen.getByRole("button", { name: "Log In" }));

    // Assert: request is sent and no email error is shown
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(screen.queryByText(EMAIL_ERROR)).not.toBeInTheDocument();
  });
});

describe("Intake phone number", () => {
  // Helper: open the Intake page and choose "Phone" as the contact method
  async function openWithPhone() {
    renderPage("/intake", <Intake />);
    const user = userEvent.setup();
    await user.selectOptions(screen.getByLabelText(/preferred contact method/i), "phone");
    return user;
  }

  /*
   * TEST 7: Only numbers are kept
   * Checks that:
   *   - Typing "(416) 555-1234" keeps only "4165551234"
   */
  test("phone field keeps only numbers", async () => {
    // Arrange: contact method is Phone
    const user = await openWithPhone();

    // Act: type a phone number with brackets, spaces and a dash
    await user.type(screen.getByLabelText(/contact information/i), "(416) 555-1234");

    // Assert: only the digits are kept
    expect(screen.getByLabelText(/contact information/i)).toHaveValue("4165551234");
  });

  /*
   * TEST 8: Maximum 10 digits
   * Checks that:
   *   - Extra digits after the 10th are not added
   */
  test("phone field stops at 10 digits", async () => {
    // Arrange: contact method is Phone
    const user = await openWithPhone();

    // Act: type 13 digits
    await user.type(screen.getByLabelText(/contact information/i), "4165551234999");

    // Assert: only the first 10 digits are kept
    expect(screen.getByLabelText(/contact information/i)).toHaveValue("4165551234");
  });
});

describe("Intake under review", () => {
  /*
   * TEST 9: Second intake while the first is under review
   * Checks that:
   *   - The message from the backend is shown to the user
   */
  test("shows message when an intake is already under review", async () => {
    // Arrange: user is logged in, backend says an intake is already under review
    localStorage.setItem("authToken", "client.jwt");
    const message =
      "You already have an intake request under review. " +
      "Please wait for it to be reviewed before submitting another request.";
    mockFetch(false, message);
    renderPage("/intake", <Intake />);

    // Act: fill the required fields and submit
    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/client's full name/i), "Sam Lee");
    await user.selectOptions(screen.getByLabelText(/preferred contact method/i), "email");
    await user.type(screen.getByLabelText(/contact information/i), "alex@mail.com");
    await user.type(screen.getByLabelText(/^overview/i), "Some overview");
    await user.type(screen.getByLabelText(/support needs/i), "Some needs");
    await user.click(screen.getByRole("button", { name: "Continue to Secure Intake" }));

    // Assert: the message is shown
    expect(await screen.findByText(message)).toBeInTheDocument();
  });
});
