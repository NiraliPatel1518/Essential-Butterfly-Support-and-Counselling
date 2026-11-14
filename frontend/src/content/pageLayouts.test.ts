import { describe, it, expect } from "vitest";
import {
  DEFAULT_SERVICES,
  PAGE_KEYS,
  PAGE_LAYOUTS,
  SERVICES_MAX,
  SERVICE_TEXT_MAX,
  SERVICE_TITLE_MAX,
  defaultPageContent,
  newServiceId,
  resolvePageContent,
  serializePageContent,
  validatePageContent,
} from "./pageLayouts";

describe("page layouts", () => {
  it("defines the three editable pages", () => {
    expect(PAGE_KEYS).toEqual(["services", "about", "privacy"]);
    expect(PAGE_LAYOUTS.services.path).toBe("/services");
    expect(PAGE_LAYOUTS.about.path).toBe("/about");
    expect(PAGE_LAYOUTS.privacy.path).toBe("/privacy");
  });

  it("only the Services page has a services list", () => {
    expect(PAGE_LAYOUTS.services.hasServices).toBe(true);
    expect(PAGE_LAYOUTS.about.hasServices).toBe(false);
    expect(PAGE_LAYOUTS.privacy.hasServices).toBe(false);
  });

  it("every slot has a unique key and its default fits its own limit", () => {
    for (const key of PAGE_KEYS) {
      const keys = PAGE_LAYOUTS[key].slots.map((slot) => slot.key);
      expect(new Set(keys).size).toBe(keys.length);

      for (const slot of PAGE_LAYOUTS[key].slots) {
        expect(slot.default.length, `${key}.${slot.key}`).toBeGreaterThan(0);
        expect(slot.default.length, `${key}.${slot.key}`).toBeLessThanOrEqual(slot.max);
      }
    }
  });

  it("the built-in services fit the service limits", () => {
    expect(DEFAULT_SERVICES.length).toBeLessThanOrEqual(SERVICES_MAX);

    for (const service of DEFAULT_SERVICES) {
      expect(service.title.length).toBeLessThanOrEqual(SERVICE_TITLE_MAX);
      expect(service.text.length).toBeLessThanOrEqual(SERVICE_TEXT_MAX);
    }
  });

  it("the built-in pages pass their own validation", () => {
    for (const key of PAGE_KEYS) {
      expect(validatePageContent(key, defaultPageContent(key))).toEqual({});
    }
  });
});

describe("defaultPageContent", () => {
  it("fills every slot with its original wording", () => {
    const content = defaultPageContent("privacy");

    expect(content.fields.title).toBe("Privacy");
    expect(Object.keys(content.fields)).toHaveLength(PAGE_LAYOUTS.privacy.slots.length);
  });

  it("gives the Services page a copy of the services, not the shared list", () => {
    const content = defaultPageContent("services");

    expect(content.services).toEqual(DEFAULT_SERVICES);
    content.services![0].title = "Changed";
    expect(DEFAULT_SERVICES[0].title).toBe("Respite");
  });

  it("gives other pages no services list", () => {
    expect(defaultPageContent("about").services).toBeUndefined();
  });
});

describe("resolvePageContent", () => {
  it("returns the built-in wording when nothing is published", () => {
    expect(resolvePageContent("about", null)).toEqual(defaultPageContent("about"));
    expect(resolvePageContent("about", undefined)).toEqual(defaultPageContent("about"));
    expect(resolvePageContent("about", "")).toEqual(defaultPageContent("about"));
  });

  it("returns the built-in wording for damaged text", () => {
    for (const bad of ["not json", "42", "null", '"text"']) {
      expect(resolvePageContent("privacy", bad)).toEqual(defaultPageContent("privacy"));
    }
  });

  it("uses a published slot and keeps the others original", () => {
    const content = resolvePageContent(
      "privacy",
      JSON.stringify({ fields: { title: "Our privacy promise" } })
    );

    expect(content.fields.title).toBe("Our privacy promise");
    expect(content.fields.p1).toBe(PAGE_LAYOUTS.privacy.slots[1].default);
  });

  it("ignores empty, non-text, too long and unknown slots", () => {
    const content = resolvePageContent(
      "privacy",
      JSON.stringify({
        fields: {
          title: "   ",
          p1: 42,
          p2: "x".repeat(221),
          linkText: "ok",
          unknownSlot: "hello",
        },
      })
    );

    expect(content.fields.title).toBe("Privacy");
    expect(content.fields.p1).toBe(PAGE_LAYOUTS.privacy.slots[1].default);
    expect(content.fields.p2).toBe(PAGE_LAYOUTS.privacy.slots[2].default);
    expect(content.fields.linkText).toBe("ok");
    expect(content.fields).not.toHaveProperty("unknownSlot");
  });

  it("reads a published services list", () => {
    const content = resolvePageContent(
      "services",
      JSON.stringify({
        fields: {},
        services: [{ id: "a", title: "Coaching", text: "Weekly coaching.", comingSoon: true }],
      })
    );

    expect(content.services).toEqual([
      { id: "a", title: "Coaching", text: "Weekly coaching.", comingSoon: true },
    ]);
  });

  it("marks a service as not coming soon unless it says so", () => {
    const content = resolvePageContent(
      "services",
      JSON.stringify({ fields: {}, services: [{ id: "a", title: "T", text: "X", comingSoon: "yes" }] })
    );

    expect(content.services![0].comingSoon).toBe(false);
  });

  it("falls back to the built-in services when the list is not usable", () => {
    const tooMany = Array.from({ length: SERVICES_MAX + 1 }, (_, i) => ({
      id: `s${i}`,
      title: "T",
      text: "X",
    }));

    const unusable = [
      [],
      "nope",
      [null],
      [{ id: 1, title: "T", text: "X" }],
      [{ id: "a", title: "  ", text: "X" }],
      [{ id: "a", title: "T", text: "" }],
      [{ id: "a", title: "T".repeat(SERVICE_TITLE_MAX + 1), text: "X" }],
      [{ id: "a", title: "T", text: "X".repeat(SERVICE_TEXT_MAX + 1) }],
      tooMany,
    ];

    for (const services of unusable) {
      const content = resolvePageContent("services", JSON.stringify({ fields: {}, services }));
      expect(content.services).toEqual(DEFAULT_SERVICES);
    }
  });

  it("never gives a page without a services list any services", () => {
    const content = resolvePageContent(
      "about",
      JSON.stringify({ fields: {}, services: [{ id: "a", title: "T", text: "X" }] })
    );

    expect(content.services).toBeUndefined();
  });
});

describe("serializePageContent", () => {
  it("writes fields only for pages without a services list", () => {
    const text = serializePageContent({ fields: { title: "Privacy" } });

    expect(JSON.parse(text)).toEqual({ fields: { title: "Privacy" } });
  });

  it("writes the services list when there is one", () => {
    const content = { fields: { a: "b" }, services: [{ id: "x", title: "T", text: "X" }] };

    expect(JSON.parse(serializePageContent(content))).toEqual(content);
  });

  it("round-trips through resolvePageContent", () => {
    const original = defaultPageContent("services");
    original.fields.heroTitle = "A new title";
    original.services!.push({ id: "extra", title: "Extra", text: "More support." , comingSoon: false});

    const again = resolvePageContent("services", serializePageContent(original));

    expect(again.fields.heroTitle).toBe("A new title");
    expect(again.services).toHaveLength(original.services!.length);
  });
});

describe("validatePageContent", () => {
  it("accepts the built-in wording", () => {
    expect(validatePageContent("about", defaultPageContent("about"))).toEqual({});
  });

  it("names an empty slot", () => {
    const content = defaultPageContent("privacy");
    content.fields.title = "   ";

    const problems = validatePageContent("privacy", content);

    expect(problems["slot-title"]).toBe("Privacy page: Title cannot be empty.");
  });

  it("treats a missing slot as empty", () => {
    const content = defaultPageContent("privacy");
    delete content.fields.p1;

    expect(validatePageContent("privacy", content)["slot-p1"]).toContain("cannot be empty");
  });

  it("names a slot that is too long", () => {
    const content = defaultPageContent("privacy");
    content.fields.title = "x".repeat(41);

    expect(validatePageContent("privacy", content)["slot-title"]).toBe(
      "Privacy page: Title must be 40 characters or fewer."
    );
  });

  it("accepts text exactly at the limit", () => {
    const content = defaultPageContent("privacy");
    content.fields.title = "x".repeat(40);

    expect(validatePageContent("privacy", content)).toEqual({});
  });

  it("requires at least one service on the Services page", () => {
    const content = defaultPageContent("services");
    content.services = [];

    expect(validatePageContent("services", content)["service-add"]).toBe(
      "Keep at least one service on the page."
    );
  });

  it("names a service with no name or description", () => {
    const content = defaultPageContent("services");
    content.services = [
      { id: "a", title: "", text: "Fine" },
      { id: "b", title: "Fine", text: "  " },
    ];

    const problems = validatePageContent("services", content);

    expect(problems["service-title-0"]).toBe("Service 1: the name cannot be empty.");
    expect(problems["service-text-1"]).toBe("Service 2: the description cannot be empty.");
    expect(problems["service-text-0"]).toBeUndefined();
  });

  it("does not check services on a page that has none", () => {
    const content = defaultPageContent("privacy");

    expect(validatePageContent("privacy", content)["service-add"]).toBeUndefined();
  });
});

describe("newServiceId", () => {
  it("starts from the next number", () => {
    expect(newServiceId([])).toBe("service-1");
    expect(newServiceId([{ id: "respite", title: "T", text: "X" }])).toBe("service-2");
  });

  it("skips ids that are already used", () => {
    const existing = [
      { id: "service-2", title: "T", text: "X" },
      { id: "other", title: "T", text: "X" },
    ];

    expect(newServiceId(existing)).toBe("service-3");
  });
});