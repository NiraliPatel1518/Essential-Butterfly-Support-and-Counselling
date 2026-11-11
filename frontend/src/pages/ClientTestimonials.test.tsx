import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ClientTestimonials from "./ClientTestimonials";
import { renderApp, jsonResponse } from "../test/render";

const LONG_TEXT = "Casey has been wonderful for our family.";

function mine(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    displayName: "Jane",
    content: "Great support.",
    photos: [],
    createdAt: "2026-01-01T10:00:00",
    ...overrides,
  };
}

function stubApi(list: unknown[] = [], post?: () => Promise<unknown>) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    if (init?.method === "POST") {
      return post ? post() : jsonResponse({});
    }
    return jsonResponse(list);
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

async function fillForm(user: ReturnType<typeof userEvent.setup>, consent = true) {
  await user.type(screen.getByLabelText(/public display name/i), "Jane");
  await user.type(screen.getByLabelText(/your testimonial/i), LONG_TEXT);
  if (consent) {
    await user.click(screen.getByLabelText(/i give permission/i));
  }
}

beforeEach(() => {
  localStorage.clear();
  localStorage.setItem("authToken", "client-token");
});

afterEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
});

describe("ClientTestimonials", () => {
  it("says so when the client has no submissions yet", async () => {
    stubApi([]);
    renderApp("/dashboard", <ClientTestimonials />);

    expect(
      await screen.findByText("You have not submitted a testimonial yet.")
    ).toBeInTheDocument();
  });

  it("lists the client's own submissions", async () => {
    stubApi([mine(), mine({ id: 2, displayName: "Second", content: "Another one." })]);
    renderApp("/dashboard", <ClientTestimonials />);

    expect(await screen.findByText("Jane")).toBeInTheDocument();
    expect(screen.getByText("Great support.")).toBeInTheDocument();
    expect(screen.getByText("Second")).toBeInTheDocument();
  });

  it("shows an error when the submissions cannot be loaded", async () => {
    vi.stubGlobal("fetch", vi.fn(() => jsonResponse({}, false, 500)));
    renderApp("/dashboard", <ClientTestimonials />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Unable to load your testimonials."
    );
  });

  it("asks for publication consent before submitting", async () => {
    const fetchMock = stubApi([]);
    const user = userEvent.setup();
    renderApp("/dashboard", <ClientTestimonials />);
    await screen.findByText("You have not submitted a testimonial yet.");

    await fillForm(user, false);
    await user.click(screen.getByRole("button", { name: "Submit Testimonial" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Please provide publication consent."
    );
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "POST")).toBe(false);
  });

  it("rejects a testimonial shorter than 10 characters", async () => {
    stubApi([]);
    const user = userEvent.setup();
    renderApp("/dashboard", <ClientTestimonials />);
    await screen.findByText("You have not submitted a testimonial yet.");

    await user.type(screen.getByLabelText(/public display name/i), "Jane");
    await user.type(screen.getByLabelText(/your testimonial/i), "Short");
    await user.click(screen.getByLabelText(/i give permission/i));
    // Skip the browser's own validation so the page's own check is tested.
    fireEvent.submit(screen.getByRole("button", { name: "Submit Testimonial" }).closest("form")!);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Your testimonial must contain at least 10 characters."
    );
  });

  it("submits the testimonial with the client token and shows a confirmation", async () => {
    const fetchMock = stubApi([]);
    const user = userEvent.setup();
    renderApp("/dashboard", <ClientTestimonials />);
    await screen.findByText("You have not submitted a testimonial yet.");

    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "Submit Testimonial" }));

    expect(await screen.findByRole("status")).toHaveTextContent(
      "Your testimonial has been submitted and is awaiting review."
    );

    const post = fetchMock.mock.calls.find(([, init]) => init?.method === "POST");
    expect(post).toBeDefined();
    expect(String(post![0])).toContain("/api/testimonials");
    expect(JSON.stringify(post![1]?.headers)).toContain("Bearer client-token");
    expect(JSON.parse(String(post![1]?.body))).toEqual({
      displayName: "Jane",
      content: LONG_TEXT,
      publicationConsent: true,
      photos: [],
    });
    expect(screen.getByLabelText(/public display name/i)).toHaveValue("");
  });

  it("shows the server message when the submission is refused", async () => {
    stubApi([], () => jsonResponse("Too many submissions", false, 429));
    const user = userEvent.setup();
    renderApp("/dashboard", <ClientTestimonials />);
    await screen.findByText("You have not submitted a testimonial yet.");

    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "Submit Testimonial" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Too many submissions");
  });

  it("tells the client to log in again when the token is missing", async () => {
    localStorage.removeItem("authToken");
    const fetchMock = stubApi([]);
    const user = userEvent.setup();
    renderApp("/dashboard", <ClientTestimonials />);
    await screen.findByText("You have not submitted a testimonial yet.");

    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "Submit Testimonial" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Your session has expired. Please log in again."
    );
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "POST")).toBe(false);
  });

  it("only accepts JPG, PNG and WEBP photos", async () => {
    stubApi([]);
    const user = userEvent.setup({ applyAccept: false });
    renderApp("/dashboard", <ClientTestimonials />);
    await screen.findByText("You have not submitted a testimonial yet.");

    const pdf = new File(["x"], "doc.pdf", { type: "application/pdf" });
    await user.upload(screen.getByLabelText(/photos \(optional/i), pdf);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Only JPG, PNG and WEBP images are allowed."
    );
  });

  it("allows at most 3 photos", async () => {
    stubApi([]);
    const user = userEvent.setup({ applyAccept: false });
    renderApp("/dashboard", <ClientTestimonials />);
    await screen.findByText("You have not submitted a testimonial yet.");

    const files = [1, 2, 3, 4].map(
      (n) => new File(["x"], `p${n}.png`, { type: "image/png" })
    );
    await user.upload(screen.getByLabelText(/photos \(optional/i), files);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "You can select a maximum of 3 photos."
    );
  });

  it("previews a valid photo and lets the client remove it", async () => {
    stubApi([]);
    const user = userEvent.setup({ applyAccept: false });
    renderApp("/dashboard", <ClientTestimonials />);
    await screen.findByText("You have not submitted a testimonial yet.");

    const png = new File(["x"], "p.png", { type: "image/png" });
    await user.upload(screen.getByLabelText(/photos \(optional/i), png);

    expect(await screen.findByText("1 photo(s) selected.")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Remove selected photos" }));
    expect(screen.queryByText("1 photo(s) selected.")).not.toBeInTheDocument();
  });
});