import { describe, expect, it, vi, afterEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Intake from "./Intake";
import { renderApp } from "../test/render";

afterEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
});

function mockFetch(success = true) {
  return vi.fn().mockImplementation((url: string) => {
    if (String(url).includes("/api/features")) {
      return Promise.resolve({
        ok: true,
        json: async () => [],
      });
    }

    return Promise.resolve({
      ok: success,
      text: async () =>
        success ? "Intake submitted" : "Submission failed",
    });
  });
}

async function fillForm() {
  const user = userEvent.setup();

  await user.type(
    screen.getByLabelText(/client.s full name/i),
    "Sam Lee"
  );

  await user.selectOptions(
    screen.getByLabelText(/preferred contact method/i),
    "email"
  );

  await user.type(
    screen.getByLabelText(/email address/i),
    "sam@example.com"
  );

  await user.type(
    screen.getByLabelText(/a little about your situation/i),
    "Overview"
  );

  await user.type(
    screen.getByLabelText(/what kind of support are you looking for/i),
    "Support needs"
  );

  return user;
}

describe("Intake", () => {
  it("renders the current intake form", () => {
    vi.stubGlobal("fetch", mockFetch());

    renderApp("/intake", <Intake />);

    expect(
      screen.getByRole("heading", {
        name: /let.s start with what matters to you/i,
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole("button", { name: /submit intake request/i })
    ).toBeInTheDocument();
  });

  it("does not submit an empty form", async () => {
    const fetchMock = mockFetch();
    vi.stubGlobal("fetch", fetchMock);

    localStorage.setItem("authToken", "client.jwt");

    renderApp("/intake", <Intake />);

    await userEvent.click(
      screen.getByRole("button", { name: /submit intake request/i })
    );

    expect(
      fetchMock.mock.calls.some(([url]) =>
        String(url).includes("/api/intake")
      )
    ).toBe(false);
  });

  it("sends valid intake data with client token", async () => {
    localStorage.setItem("authToken", "client.jwt");

    const fetchMock = mockFetch();
    vi.stubGlobal("fetch", fetchMock);

    renderApp("/intake", <Intake />);

    const user = await fillForm();

    await user.click(
      screen.getByRole("button", { name: /submit intake request/i })
    );

    await waitFor(() => {
      expect(
        fetchMock.mock.calls.filter(([url]) =>
          String(url).includes("/api/intake")
        )
      ).toHaveLength(1);
    });

    const intakeRequest = fetchMock.mock.calls.find(([url]) =>
      String(url).includes("/api/intake")
    );

    expect(intakeRequest).toBeDefined();
    expect(intakeRequest?.[1].headers.Authorization).toBe(
      "Bearer client.jwt"
    );
  });

  it("shows backend error on unsuccessful submission", async () => {
    localStorage.setItem("authToken", "client.jwt");

    const fetchMock = mockFetch(false);
    vi.stubGlobal("fetch", fetchMock);

    renderApp("/intake", <Intake />);

    const user = await fillForm();

    await user.click(
      screen.getByRole("button", { name: /submit intake request/i })
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /submission failed/i
    );
  });
});