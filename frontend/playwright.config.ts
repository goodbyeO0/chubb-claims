import { defineConfig, devices } from "@playwright/test";
const integration = process.argv.some((arg) => arg.includes("integration"));
export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  workers: 1,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [["list"], ["html", { open: "never" }]],
  use: { trace: "retain-on-failure", screenshot: "only-on-failure" },
  projects: [
    {
      name: "fixture",
      testMatch: "fixture.spec.ts",
      use: { ...devices["Desktop Chrome"], baseURL: "http://127.0.0.1:14200" },
    },
    {
      name: "integration",
      testMatch: "integration.spec.ts",
      use: {
        ...devices["Desktop Chrome"],
        baseURL: process.env["E2E_BASE_URL"] || "http://localhost:18080",
      },
    },
  ],
  webServer: integration
    ? undefined
    : {
        command: "npm start -- --host 127.0.0.1",
        url: "http://127.0.0.1:14200",
        reuseExistingServer: false,
        timeout: 120_000,
      },
});
