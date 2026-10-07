import { describe, expect, test } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Contact from "./Contact";
import { mockFetch, mockFetchNetworkError, renderPage } from "../test/helpers";

/*
 * UC-02: Submit Contact Message
 *
 * This file has 5 tests for the Contact page:
 *   Test 1 - Valid message is sent to the correct API in the correct format
 *   Test 2 - Successful send -> thank you message, form is cleared
 *   Test 3 - Backend returns an error -> error message is shown
 *   Test 4 - Backend is not running -> error message is shown
 *   Test 5 - Required fields are empty -> nothing is sent
 *
 * The backend is NOT needed. mockFetch() creates a fake backend response.
 * No login is needed, because the contact form is public.
 */

// Helper: fills in all fields of the contact form.
// It does not click the "Send Message" button.
async function fillForm() {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText(/full name/i), "Jane Doe");
  await user.type(screen.getByLabelText(/email address/i), "jane@mail.com");
  await user.selectOptions(screen.getByLabelText(/subject/i), "services");
  await user.type(screen.getByLabelText(/message/i), "Hello, I have a question.");
  return user;
}

// Helper: finds the "Send Message" button.
const sendButton = () => screen.getByRole("button", { name: "Send Message" });

describe("Contact page", () => {
  /*
   * TEST 1: Correct request is sent
   * Checks that:
   *   - The request goes to /api/contact using POST
   *   - Name, email, subject and message are all sent
   */
  test("sends the message to /api/contact", async () => {
    // Arrange: backend will answer "success", open the Contact page
    const fetchMock = mockFetch(true, "Your message has been submitted successfully");
    renderPage("/contact", <Contact />);

    // Act: fill the form and click "Send Message"
    const user = await fillForm();
    await user.click(sendButton());

    // Assert: correct URL, method and data
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/contact");
    expect(options.method).toBe("POST");
    expect(JSON.parse(options.body)).toEqual({
      fullName: "Jane Doe",
      email: "jane@mail.com",
      subject: "services",
      message: "Hello, I have a question.",
    });
  });

  /*
   * TEST 2: Successful send
   * Checks that:
   *   - The thank you message is shown
   *   - The form is empty again
   */
  test("shows thank you message and clears the form", async () => {
    // Arrange: backend will answer "success"
    mockFetch(true, "Your message has been submitted successfully");
    renderPage("/contact", <Contact />);

    // Act: fill the form and click "Send Message"
    const user = await fillForm();
    await user.click(sendButton());

    // Assert: thank you message and empty form
    expect(await screen.findByRole("status")).toHaveTextContent(
      "Thank you. Your message has been received."
    );
    expect(screen.getByLabelText(/full name/i)).toHaveValue("");
    expect(screen.getByLabelText(/message/i)).toHaveValue("");
  });

  /*
   * TEST 3: Backend returns an error
   * Checks that:
   *   - The error message from the backend is shown
   */
  test("shows backend error message", async () => {
    // Arrange: backend will answer with an error
    mockFetch(false, "Please enter a valid email address");
    renderPage("/contact", <Contact />);

    // Act: fill the form and click "Send Message"
    const user = await fillForm();
    await user.click(sendButton());

    // Assert: error is shown
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Please enter a valid email address"
    );
  });

  /*
   * TEST 4: Backend is not running
   * Checks that:
   *   - An error message is shown instead of a thank you message
   */
  test("shows an error when the backend is down", async () => {
    // Arrange: fake a network failure (server is off)
    mockFetchNetworkError();
    renderPage("/contact", <Contact />);

    // Act: fill the form and click "Send Message"
    const user = await fillForm();
    await user.click(sendButton());

    // Assert: an error is shown and no thank you message
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  /*
   * TEST 5: Required fields are empty
   * Checks that:
   *   - Nothing is sent when the user clicks "Send Message" with an empty form
   */
  test("does not send when required fields are empty", async () => {
    // Arrange: open the Contact page
    const fetchMock = mockFetch(true);
    renderPage("/contact", <Contact />);

    // Act: click "Send Message" without filling anything
    await userEvent.click(sendButton());

    // Assert: backend is not called
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
