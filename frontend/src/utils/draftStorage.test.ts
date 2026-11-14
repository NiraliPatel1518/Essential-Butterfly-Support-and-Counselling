import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { clearDraft, loadDraft, saveDraft } from "./draftStorage";

beforeEach(() => sessionStorage.clear());
afterEach(() => vi.restoreAllMocks());

describe("draftStorage", () => {
  it("saves and loads text with the website's own prefix", () => {
    saveDraft("page:services", { title: "Hello" });

    expect(sessionStorage.getItem("essential-butterfly:draft:page:services")).toBe('{"title":"Hello"}');
    expect(loadDraft<{ title: string }>("page:services")).toEqual({ title: "Hello" });
  });

  it("returns null when nothing was saved", () => {
    expect(loadDraft("missing")).toBeNull();
  });

  it("keeps different keys apart", () => {
    saveDraft("a", 1);
    saveDraft("b", 2);

    expect(loadDraft("a")).toBe(1);
    expect(loadDraft("b")).toBe(2);
  });

  it("replaces an older draft with the same key", () => {
    saveDraft("a", "old");
    saveDraft("a", "new");

    expect(loadDraft("a")).toBe("new");
  });

  it("clears one draft and leaves the others", () => {
    saveDraft("a", 1);
    saveDraft("b", 2);

    clearDraft("a");

    expect(loadDraft("a")).toBeNull();
    expect(loadDraft("b")).toBe(2);
  });

  it("clearing a draft that does not exist is harmless", () => {
    expect(() => clearDraft("nothing")).not.toThrow();
  });

  it("returns null for stored text that is not valid JSON", () => {
    sessionStorage.setItem("essential-butterfly:draft:broken", "{not json");

    expect(loadDraft("broken")).toBeNull();
  });

  it("does not crash when the browser refuses storage", () => {
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    vi.spyOn(Storage.prototype, "removeItem").mockImplementation(() => {
      throw new Error("blocked");
    });

    expect(() => saveDraft("a", 1)).not.toThrow();
    expect(loadDraft("a")).toBeNull();
    expect(() => clearDraft("a")).not.toThrow();
  });
});