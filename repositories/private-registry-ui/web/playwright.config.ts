import { defineConfig, devices } from "@playwright/test";

const isCi = Boolean(process.env["CI"]);
const isFullMatrix = process.env["PLAYWRIGHT_FULL_MATRIX"] === "true";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: isCi,
  retries: isCi ? 2 : 0,
  ...(isCi ? { workers: 1 } : {}),
  reporter: isCi
    ? [["line"], ["html", { open: "never" }]]
    : [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: "http://127.0.0.1:4173",
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    ...(isFullMatrix
      ? [
          { name: "firefox", use: { ...devices["Desktop Firefox"] } },
          { name: "webkit", use: { ...devices["Desktop Safari"] } },
        ]
      : []),
  ],
  webServer: {
    command: "pnpm preview --host 127.0.0.1 --port 4173",
    url: "http://127.0.0.1:4173",
    reuseExistingServer: !isCi,
    timeout: 30_000,
  },
});
