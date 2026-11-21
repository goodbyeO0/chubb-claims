import { test, expect, Browser, BrowserContext, Page } from "@playwright/test";
import { login, logout, submit, confirm, pickup } from "./helpers";
import { Exposure } from "../src/app/api";

test.setTimeout(120_000);
const contexts: BrowserContext[] = [];
const errors: string[] = [];
test.afterEach(async () => {
  for (const c of contexts.splice(0)) await c.close();
  expect(errors.splice(0), "Browser runtime errors").toEqual([]);
});
async function actor(
  browser: Browser,
  baseURL: string | undefined,
  name: string,
) {
  const context = await browser.newContext({ baseURL });
  contexts.push(context);
  const page = await context.newPage();
  page.on("pageerror", (e) => errors.push(e.message));
  await login(page, name);
  return page;
}
async function exposure(page: Page, currency: string) {
  const response = await page.request.get("/api/staff/reports/exposure");
  expect(response.status()).toBe(200);
  const body = (await response.json()) as Exposure;
  const row = body.byCurrency.find((c) => c.currency === currency);
  return {
    open: row?.openClaims ?? 0,
    unknown: row?.awaitingEstimate ?? 0,
    cents: BigInt(
      (row?.knownOutstanding ?? "0.00").replace(".", ""),
    ).toString(),
  };
}

test("I1/I2/I4: real session reload, claimant handoff, CSRF and full reserve release", async ({
  browser,
  baseURL,
}) => {
  const aina = await actor(browser, baseURL, "aina"),
    farah = await actor(browser, baseURL, "farah");
  await expect(aina.locator(".fixture-banner")).toHaveCount(0);
  const baseline = await exposure(farah, "MYR");
  const submission = aina.waitForRequest(
    (r) => new URL(r.url()).pathname === "/api/claims" && r.method() === "POST",
  );
  const c = await submit(aina, "Integration bumper incident " + Date.now());
  expect(
    Boolean((await submission).headers()["x-xsrf-token"]),
    "Angular supplies native CSRF header",
  ).toBe(true);
  const cookies = await aina.context().cookies();
  const session = cookies.find((c) => c.name === "JSESSIONID");
  expect(session?.httpOnly).toBe(true);
  expect(session?.sameSite).toBe("Lax");
  if (baseURL?.startsWith("https://")) {
    expect(session?.secure, "HTTPS session cookie must be Secure").toBe(true);
    expect(cookies.find((c) => c.name === "XSRF-TOKEN")?.secure).toBe(true);
  }
  await farah.getByRole("button", { name: "Unassigned", exact: true }).click();
  await expect(
    farah.getByRole("link", { name: c.ref, exact: true }),
  ).toBeVisible();
  const restored = farah.waitForResponse(
    (r) =>
      new URL(r.url()).pathname === "/api/staff/claims" && r.status() === 200,
  );
  await farah.reload();
  await restored;
  await expect(
    farah.getByRole("heading", { name: "Work queue" }),
  ).toBeVisible();
  await pickup(farah, c.id);
  await farah
    .getByLabel("Question for claimant")
    .fill("Please share the workshop estimate.");
  await farah.getByRole("button", { name: "Send question" }).click();
  await expect(farah.locator(".badge.large")).toHaveText(
    "Awaiting information",
  );
  await aina.goto("/my-claims/" + c.id);
  await aina.getByLabel("Your reply").fill("Initial estimate is MYR 5,000.");
  await aina.getByRole("button", { name: "Send reply" }).click();
  await expect(aina.locator(".badge.large")).toHaveText("Under review");
  await farah.reload();
  await expect(
    farah.getByText("Initial estimate is MYR 5,000.", { exact: true }).first(),
  ).toBeVisible();
  await farah.getByLabel("Reserve amount (MYR)").fill("5000.00");
  await farah.getByRole("button", { name: "Save assessment" }).click();
  await confirm(farah, "assessment");
  await expect(farah.locator(".amount")).toHaveText("MYR 5000.00");
  await expect
    .poll(() => exposure(farah, "MYR"), {
      timeout: 60_000,
      intervals: [250, 500, 1000],
    })
    .toEqual({
      open: baseline.open + 1,
      unknown: baseline.unknown,
      cents: (BigInt(baseline.cents) + 500000n).toString(),
    });
  const privateResponse = aina.waitForResponse(
    (r) =>
      new URL(r.url()).pathname === "/api/claims/" + c.id && r.status() === 200,
  );
  await aina.reload();
  const publicBody = await (await privateResponse).json();
  for (const field of [
    "reserve",
    "officerId",
    "claimantId",
    "actorId",
    "STAFF_ONLY",
    "RESERVE_SET",
  ])
    expect(JSON.stringify(publicBody)).not.toContain(field);
  await expect(
    aina.getByRole("heading", { name: "Claim history" }),
  ).toBeVisible();
  await expect(aina.locator("main")).not.toContainText(
    /reserve|5000\.00|Staff only/i,
  );
  await farah.getByRole("link", { name: "Reports", exact: true }).click();
  await expect(
    farah.getByRole("heading", { name: "Outstanding exposure" }),
  ).toBeVisible();
  await farah.screenshot({
    path: "test-results/integrated-reports.png",
    fullPage: true,
  });
  await farah.goto("/staff/claims/" + c.id);
  await farah.getByLabel("Settlement amount (MYR)").fill("4200.00");
  await farah.getByRole("button", { name: "Review settlement" }).click();
  await confirm(farah, "settlement");
  await expect(farah.locator(".badge.large")).toHaveText("Settled");
  await expect
    .poll(() => exposure(farah, "MYR"), {
      timeout: 60_000,
      intervals: [250, 500, 1000],
    })
    .toEqual(baseline);
  await aina.reload();
  await expect(
    aina.getByRole("heading", { name: "Settlement recorded" }),
  ).toBeVisible();
  await expect(aina.locator(".amount")).toHaveText("MYR 4200.00");
  const missingApi = await farah.request.get("/api/no-such-endpoint");
  expect(missingApi.status()).toBe(404);
  expect((await missingApi.json()).code).toBe("NOT_FOUND");
  const missingAsset = await farah.request.get("/missing-asset.js");
  expect(missingAsset.status()).toBe(404);
  expect(missingAsset.headers()["content-type"]).not.toContain("text/html");
  await logout(aina);
  expect((await aina.request.get("/api/auth/me")).status()).toBe(401);
  await login(aina, "aina");
});

test("I3: independent property rejection and cross-owner isolation", async ({
  browser,
  baseURL,
}) => {
  const daniel = await actor(browser, baseURL, "daniel"),
    arjun = await actor(browser, baseURL, "arjun"),
    aina = await actor(browser, baseURL, "aina");
  const baseline = await exposure(arjun, "SGD");
  const c = await submit(
    daniel,
    "Duplicate property incident " + Date.now(),
    "SG",
  );
  await pickup(arjun, c.id);
  await arjun
    .getByLabel("Question for claimant")
    .fill("Is this the same incident you reported earlier?");
  await arjun.getByRole("button", { name: "Send question" }).click();
  await expect(arjun.locator(".badge.large")).toHaveText(
    "Awaiting information",
  );
  await daniel.goto("/my-claims/" + c.id);
  await daniel
    .getByLabel("Your reply")
    .fill("Yes, I accidentally submitted the same incident twice.");
  await daniel.getByRole("button", { name: "Send reply" }).click();
  await expect(daniel.locator(".badge.large")).toHaveText("Under review");
  await arjun.reload();
  await arjun
    .getByLabel("Rejection reason")
    .fill("Duplicate property incident confirmed by the claimant.");
  await arjun.getByRole("button", { name: "Review rejection" }).click();
  await confirm(arjun, "rejection");
  await expect(arjun.locator(".badge.large")).toHaveText("Rejected");
  await daniel.reload();
  await expect(daniel.locator(".decision")).toContainText(
    "Duplicate property incident confirmed by the claimant.",
  );
  const other = await aina.request.get("/api/claims/" + c.id);
  expect(other.status()).toBe(404);
  expect((await other.json()).code).toBe("NOT_FOUND");
  const list = await (await aina.request.get("/api/claims")).json();
  expect(list.some((row: { id: string }) => row.id === c.id)).toBe(false);
  await expect
    .poll(() => exposure(arjun, "SGD"), {
      timeout: 60_000,
      intervals: [250, 500, 1000],
    })
    .toEqual(baseline);
});

test("Real stale settlement refreshes and requires a second explicit confirmation", async ({
  browser,
  baseURL,
}) => {
  const aina = await actor(browser, baseURL, "aina"),
    farah = await actor(browser, baseURL, "farah");
  const c = await submit(aina, "Concurrent assessment " + Date.now());
  await pickup(farah, c.id);
  const second = await farah.context().newPage();
  second.on("pageerror", (e) => errors.push(e.message));
  await second.goto("/staff/claims/" + c.id);
  await second.getByLabel("Reserve amount (MYR)").fill("50.00");
  await second.getByRole("button", { name: "Save assessment" }).click();
  await confirm(second, "assessment");
  await expect(second.locator(".amount")).toHaveText("MYR 50.00");
  await farah.getByLabel("Settlement amount (MYR)").fill("40.00");
  await farah.getByRole("button", { name: "Review settlement" }).click();
  const stale = farah.waitForResponse(
    (r) => r.url().endsWith("/settle") && r.status() === 409,
  );
  await confirm(farah, "settlement");
  await stale;
  await expect(farah.getByRole("status")).toContainText("This claim changed");
  await expect(farah.locator(".badge.large")).toHaveText("Under review");
  await expect(farah.locator(".amount")).toHaveText("MYR 50.00");
  await expect(farah.getByRole("dialog")).not.toBeVisible();
  await farah.getByRole("button", { name: "Review settlement" }).click();
  await confirm(farah, "settlement");
  await expect(farah.locator(".badge.large")).toHaveText("Settled");
});

test("A late API failure stays an error and never loads fixture data", async ({
  browser,
  baseURL,
}) => {
  const aina = await actor(browser, baseURL, "aina");
  await aina.route("**/api/claims", (route) =>
    route.fulfill({
      status: 503,
      contentType: "application/json",
      body: JSON.stringify({
        code: "UNAVAILABLE",
        message: "Temporary test outage.",
      }),
    }),
  );
  await aina.reload();
  await expect(aina.getByRole("alert")).toContainText("Temporary test outage.");
  await expect(aina.locator(".fixture-banner")).toHaveCount(0);
  await expect(aina.locator(".case-link")).toHaveCount(0);
  await aina.unroute("**/api/claims");
  await aina.getByRole("button", { name: "Try again" }).click();
  await expect(aina.getByRole("alert")).toHaveCount(0);
});
