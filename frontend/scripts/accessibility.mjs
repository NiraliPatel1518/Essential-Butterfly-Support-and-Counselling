import { chromium } from "playwright";
import AxeBuilder from "@axe-core/playwright";
import { mkdir, writeFile } from "node:fs/promises";

const baseURL = process.env.A11Y_BASE_URL || "http://localhost:5173";

const routes = [
  ["/", /support to help/i],
  ["/contact", /love to hear/i],
  ["/resources", /resource/i],
  ["/memory-wall", /memory wall/i],
  ["/appointments", /appointment/i],
  ["/signup", /create your/i],
  ["/login", /support starts/i],
  ["/forgot-password", /forgot your password/i],
];

const report = [];
const browser = await chromium.launch();

try {
  for (const width of [1280, 390]) {
    for (const [path, expectedHeading] of routes) {
      const context = await browser.newContext({
        viewport: { width, height: 844 },
      });

      try {
        const page = await context.newPage();
        const response = await page.goto(new URL(path, baseURL).href);

        if (!response) {
          throw new Error("The page did not return an HTTP response.");
        }

        if (!response.ok()) {
          throw new Error(`Page returned HTTP ${response.status()}`);
        }

        // Confirm the expected page is visible before scanning it.
        await page
          .getByRole("heading", { level: 1, name: expectedHeading })
          .first()
          .waitFor({ state: "visible", timeout: 10000 });

        const actualPath = new URL(page.url()).pathname;
        if (actualPath !== path) {
          throw new Error(`Expected ${path}, but reached ${actualPath}`);
        }

        const { violations, incomplete } = await new AxeBuilder({ page })
          .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
          .analyze();

        report.push({ path, width, violations, incomplete });

        console.log(
          `${width}px ${path}: ${violations.length} violations; ` +
          `${incomplete.length} items need manual review`
        );

        for (const issue of violations) {
          console.log(`  ${issue.id}: ${issue.help}`);
          for (const node of issue.nodes) {
            console.log("    Affected element:", node.target);
          }
        }

        if (violations.length > 0) {
          process.exitCode = 1;
        }
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        report.push({ path, width, error: message });
        console.error(`${width}px ${path}: ${message}`);
        process.exitCode = 1;
      } finally {
        await context.close();
      }
    }
  }
} finally {
  await browser.close();
  await mkdir("test-results", { recursive: true });
  await writeFile(
    "test-results/accessibility.json",
    JSON.stringify(report, null, 2),
    "utf8"
  );
  console.log("Report saved to test-results/accessibility.json");
}