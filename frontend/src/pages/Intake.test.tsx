import { describe, expect, test } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Intake from "./Intake";
import { mockFetch, renderPage } from "../test/helpers";

/*
 * UC-02: Submit Intake Request
 *
 * This file has 4 tests for the Intake page:
 *   Test 1 - User is not logged in -> form is blocked
 *   Test 2 - User is logged in and form is valid -> form is sent and cleared
 *   Test 3 - Backend returns an error -> error message is shown
 *   Test 4 - Required fields are empty -> form is not sent
 *
 * The backend is NOT needed. mockFetch() creates a fake backend response.
 */

// Helper: fills in all required fields of the intake form.
// It does not click the submit button.
async function fillRequiredFields() {
  const user = userEvent.setup();

  await user.type(
    screen.getByLabelText(/client's full name/i),
    "Sam Lee"
  );

  await user.selectOptions(
    screen.getByLabelText(/preferred contact method/i),
    "email"
  );

  await user.type(
    screen.getByLabelText(/contact information/i),
    "alex@mail.com"
  );

  await user.type(
    screen.getByLabelText(/^overview/i),
    "Some overview"
  );

  await user.type(
    screen.getByLabelText(/support needs/i),
    "Some needs"
  );

  return user;
}

// Helper: finds the submit button of the intake form.
const submitButton = () =>
  screen.getByRole("button", { name: "Continue to Secure Intake" });

describe("Intake page", () => {
  /*
   * TEST 1: User is not logged in
   * Checks that:
   *   - The message "Please log in before submitting the intake form." is shown
   *   - No request is sent to the backend
   */
  test("asks user to log in when there is no token", async () => {
    // Arrange: fake backend, no token saved, open the Intake page
    const fetchMock = mockFetch(true);
    renderPage("/intake", <Intake />);

    // Act: fill the form and click submit
    const user = await fillRequiredFields();
    await user.click(submitButton());

    // Assert: login message is shown and backend is not called
    expect(
      await screen.findByText(
        "Please log in before submitting the intake form."
      )
    ).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  /*
   * TEST 2: Successful submission
   * Checks that:
   *   - The request goes to the correct API URL
   *   - The login token is sent in the Authorization header
   *   - The form data is sent correctly
   *   - A success message is shown
   *   - The form is cleared after submission
   */
  test("sends form with Bearer token and clears form on success", async () => {
    // Arrange: user is logged in, backend will answer "success"
    localStorage.setItem("authToken", "client.jwt");
    const fetchMock = mockFetch(
      true,
      "Intake submission received successfully"
    );
    renderPage("/intake", <Intake />);

    // Act: fill the form and click submit
    const user = await fillRequiredFields();
    await user.click(submitButton());

    // Assert: correct URL and token
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/intake");
    expect(options.headers.Authorization).toBe("Bearer client.jwt");

    // Assert: correct form data was sent
    const body = JSON.parse(options.body);
    expect(body.clientFullName).toBe("Sam Lee");
    expect(body.preferredContactMethod).toBe("email");
    expect(body.supportNeeds).toBe("Some needs");

    // Assert: success message is shown and the form is empty again
    expect(
      await screen.findByText(/submitted successfully/i)
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText(/client's full name/i)
    ).toHaveValue("");
  });

  /*
   * TEST 3: Backend returns an error
   * Checks that:
   *   - The error message from the backend is shown to the user
   */
  test("shows backend error message", async () => {
    // Arrange: user is logged in, backend will answer with an error
    localStorage.setItem("authToken", "client.jwt");
    mockFetch(false, "User account not found");
    renderPage("/intake", <Intake />);

    // Act: fill the form and click submit
    const user = await fillRequiredFields();
    await user.click(submitButton());

    // Assert: backend error is shown on the page
    expect(
      await screen.findByText("User account not found")
    ).toBeInTheDocument();
  });

  /*
   * TEST 4: Required fields are empty
   * Checks that:
   *   - The form is not sent when the user clicks submit without filling it in
   */
  test("does not submit when required fields are empty", async () => {
    // Arrange: user is logged in, open the Intake page
    localStorage.setItem("authToken", "client.jwt");
    const fetchMock = mockFetch(true);
    renderPage("/intake", <Intake />);

    // Act: click submit without filling anything
    await userEvent.click(submitButton());

    // Assert: backend is not called
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
