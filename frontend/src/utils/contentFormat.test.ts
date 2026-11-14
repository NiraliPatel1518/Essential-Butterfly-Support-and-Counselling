import { describe, it, expect } from "vitest";
import { formatFileSize } from "./contentFormat";

describe("formatFileSize", () => {
  it("shows small files in KB, never below 1 KB", () => {
    expect(formatFileSize(0)).toBe("1 KB");
    expect(formatFileSize(1)).toBe("1 KB");
    expect(formatFileSize(1024)).toBe("1 KB");
  });

  it("rounds to the nearest KB below 1 MB", () => {
    expect(formatFileSize(1536)).toBe("2 KB");
    expect(formatFileSize(500 * 1024)).toBe("500 KB");
    expect(formatFileSize(1024 * 1024 - 1)).toBe("1024 KB");
  });

  it("shows one decimal for MB sizes under 10 MB", () => {
    expect(formatFileSize(1024 * 1024)).toBe("1.0 MB");
    expect(formatFileSize(2.5 * 1024 * 1024)).toBe("2.5 MB");
  });

  it("shows whole MB from 10 MB up", () => {
    expect(formatFileSize(10 * 1024 * 1024)).toBe("10 MB");
    expect(formatFileSize(25.4 * 1024 * 1024)).toBe("25 MB");
  });
});