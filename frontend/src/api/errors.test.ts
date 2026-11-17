import { describe, expect, test } from "vitest";
import { FriendlyApiError, networkErrorMessage, readFriendlyError } from "./errors";

/* errors.ts turns server and network problems into messages a client can read. */

function reply(status: number, body = "") {
  return { status, text: () => Promise.resolve(body) } as unknown as Response;
}

describe("FriendlyApiError", () => {
  test("keeps its message and status", () => {
    const error = new FriendlyApiError("Nope", 403);

    expect(error.message).toBe("Nope");
    expect(error.status).toBe(403);
    expect(error.name).toBe("FriendlyApiError");
    expect(error).toBeInstanceOf(Error);
  });

  test("works without a status", () => {
    expect(new FriendlyApiError("Nope").status).toBeUndefined();
  });
});

describe("readFriendlyError: messages from the server", () => {
  test("uses the message field", async () => {
    expect(await readFriendlyError(reply(400, JSON.stringify({ message: "Email is taken." })))).toBe("Email is taken.");
  });

  test("uses the error field when there is no message", async () => {
    expect(await readFriendlyError(reply(400, JSON.stringify({ error: "Bad code." })))).toBe("Bad code.");
  });

  test("uses the detail field when there is no message or error", async () => {
    expect(await readFriendlyError(reply(400, JSON.stringify({ detail: "Too short." })))).toBe("Too short.");
  });

  test("uses a plain text answer", async () => {
    expect(await readFriendlyError(reply(400, "Please try a different name."))).toBe("Please try a different name.");
  });

  test("removes spaces around the message", async () => {
    expect(await readFriendlyError(reply(400, JSON.stringify({ message: "  Spaced out.  " })))).toBe("Spaced out.");
  });

  test("ignores a blank message and uses the status message", async () => {
    expect(await readFriendlyError(reply(404, JSON.stringify({ message: "   " })))).toBe(
      "The requested information could not be found."
    );
  });

  test("ignores a message that is not text", async () => {
    expect(await readFriendlyError(reply(403, JSON.stringify({ message: 42 })))).toBe(
      "You do not have permission to complete this action."
    );
  });
});

describe("readFriendlyError: technical messages are hidden", () => {
  test.each(["Request failed (500).", "Error 500", "500 Internal Server Error", "request failed 404"])(
    "replaces '%s' with a friendly message",
    async (technical) => {
      const text = await readFriendlyError(reply(500, technical));

      expect(text).toBe("Something went wrong on our side. Please try again shortly.");
    }
  );
});

describe("readFriendlyError: status messages", () => {
  test.each([
    [400, "Some information was not accepted. Please review the form and try again."],
    [401, "Your session has expired. Please sign in again."],
    [403, "You do not have permission to complete this action."],
    [404, "The requested information could not be found."],
    [408, "The request took too long. Please try again."],
    [409, "This request conflicts with information already on file."],
    [413, "The selected file is too large. Please choose a smaller file."],
    [429, "Too many requests were sent. Please wait a moment and try again."],
    [500, "Something went wrong on our side. Please try again shortly."],
    [502, "The service is temporarily unavailable. Please try again shortly."],
    [503, "The service is temporarily unavailable. Please try again shortly."],
    [504, "The service took too long to respond. Please try again."],
  ])("status %i has its own message", async (status, message) => {
    expect(await readFriendlyError(reply(status))).toBe(message);
  });

  test("uses the general message for an unknown status", async () => {
    expect(await readFriendlyError(reply(418))).toBe("We could not complete your request. Please try again.");
  });

  test("uses the fallback given by the page for an unknown status", async () => {
    expect(await readFriendlyError(reply(418), "Unable to retrieve your intake status.")).toBe(
      "Unable to retrieve your intake status."
    );
  });

  test("falls back to the status message when the answer cannot be read", async () => {
    const broken = { status: 503, text: () => Promise.reject(new Error("stream closed")) } as unknown as Response;

    expect(await readFriendlyError(broken)).toBe("The service is temporarily unavailable. Please try again shortly.");
  });
});

describe("networkErrorMessage", () => {
  test("keeps the message of a FriendlyApiError", () => {
    expect(networkErrorMessage(new FriendlyApiError("Already friendly."))).toBe("Already friendly.");
  });

  test("explains a lost connection (TypeError from fetch)", () => {
    expect(networkErrorMessage(new TypeError("Failed to fetch"))).toBe(
      "We cannot connect to the service right now. Check your connection and try again."
    );
  });

  test("keeps the message of an ordinary error", () => {
    expect(networkErrorMessage(new Error("Your code has expired."))).toBe("Your code has expired.");
  });

  test("hides a technical error message", () => {
    expect(networkErrorMessage(new Error("Request failed (500)."))).toBe(
      "We could not complete your request. Please try again."
    );
  });

  test("hides an empty error message", () => {
    expect(networkErrorMessage(new Error(""))).toBe("We could not complete your request. Please try again.");
  });

  test("handles something that is not an error at all", () => {
    expect(networkErrorMessage("boom")).toBe("We could not complete your request. Please try again.");
    expect(networkErrorMessage(null)).toBe("We could not complete your request. Please try again.");
  });
});