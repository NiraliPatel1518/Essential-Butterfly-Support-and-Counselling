import { describe, it, expect } from "vitest";
import {
  STATUS_LABELS,
  WEEKDAYS,
  dayKey,
  formatDateTime,
  formatDay,
  formatRange,
  formatTime,
  minutesLabel,
  parseLocal,
} from "./appointmentFormat";

describe("WEEKDAYS", () => {
  it("starts on Monday and ends on Sunday", () => {
    expect(WEEKDAYS).toHaveLength(7);
    expect(WEEKDAYS[0]).toBe("Monday");
    expect(WEEKDAYS[6]).toBe("Sunday");
  });
});

describe("parseLocal", () => {
  it("reads the date and time exactly as written, with no time zone shift", () => {
    const date = parseLocal("2026-11-16T10:30:00");

    expect(date.getFullYear()).toBe(2026);
    expect(date.getMonth()).toBe(10);
    expect(date.getDate()).toBe(16);
    expect(date.getHours()).toBe(10);
    expect(date.getMinutes()).toBe(30);
  });

  it("reads a time without seconds", () => {
    const date = parseLocal("2026-01-05T08:15");

    expect(date.getHours()).toBe(8);
    expect(date.getMinutes()).toBe(15);
  });

  it("uses midnight when there is no time part", () => {
    const date = parseLocal("2026-03-09");

    expect(date.getDate()).toBe(9);
    expect(date.getHours()).toBe(0);
    expect(date.getMinutes()).toBe(0);
  });
});

describe("formatDay", () => {
  it("writes the weekday, month, day and year", () => {
    const text = formatDay("2026-11-16T10:00:00");

    expect(text).toContain("Monday");
    expect(text).toContain("November");
    expect(text).toContain("16");
    expect(text).toContain("2026");
  });

  it("gets the weekday right for another date", () => {
    expect(formatDay("2026-11-22T09:00:00")).toContain("Sunday");
  });
});

describe("formatTime", () => {
  it("shows the hour and minutes", () => {
    expect(formatTime("2026-11-16T10:30:00")).toMatch(/10:30/);
  });

  it("shows a morning and an afternoon time differently", () => {
    const morning = formatTime("2026-11-16T09:00:00");
    const afternoon = formatTime("2026-11-16T15:00:00");

    expect(morning).toMatch(/9:00/);
    expect(afternoon).toMatch(/3:00/);
    expect(morning).not.toBe(afternoon);
  });
});

describe("formatRange", () => {
  it("joins the start and end time with the word to", () => {
    const text = formatRange("2026-11-16T10:00:00", "2026-11-16T11:00:00");

    expect(text).toMatch(/10:00.* to 11:00/);
  });
});

describe("formatDateTime", () => {
  it("joins the day and the time", () => {
    const text = formatDateTime("2026-11-16T14:45:00");

    expect(text).toContain("Monday");
    expect(text).toContain("November");
    expect(text).toMatch(/2:45/);
  });
});

describe("dayKey", () => {
  it("returns only the date part", () => {
    expect(dayKey("2026-11-16T10:00:00")).toBe("2026-11-16");
  });

  it("returns a plain date unchanged", () => {
    expect(dayKey("2026-11-16")).toBe("2026-11-16");
  });
});

describe("minutesLabel", () => {
  it("says 1 hour for 60 minutes", () => {
    expect(minutesLabel(60)).toBe("1 hour");
  });

  it("says hours for whole hours", () => {
    expect(minutesLabel(120)).toBe("2 hours");
  });

  it("says minutes when it is not a whole hour", () => {
    expect(minutesLabel(30)).toBe("30 minutes");
    expect(minutesLabel(45)).toBe("45 minutes");
    expect(minutesLabel(90)).toBe("90 minutes");
  });
});

describe("STATUS_LABELS", () => {
  it("has a word and a symbol for every status", () => {
    for (const status of ["REQUESTED", "CONFIRMED", "DECLINED", "CANCELLED", "COMPLETED"]) {
      expect(STATUS_LABELS[status].label.length).toBeGreaterThan(0);
      expect(STATUS_LABELS[status].symbol.length).toBeGreaterThan(0);
    }
  });

  it("uses plain words so colour is never the only signal", () => {
    expect(STATUS_LABELS.REQUESTED.label).toBe("Requested");
    expect(STATUS_LABELS.CONFIRMED.label).toBe("Confirmed");
    expect(STATUS_LABELS.DECLINED.label).toBe("Declined");
    expect(STATUS_LABELS.CANCELLED.label).toBe("Cancelled");
    expect(STATUS_LABELS.COMPLETED.label).toBe("Completed");
  });

  it("gives every status a different symbol", () => {
    const symbols = Object.values(STATUS_LABELS).map((entry) => entry.symbol);

    expect(new Set(symbols).size).toBe(symbols.length);
  });
});