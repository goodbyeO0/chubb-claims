import { test as base, expect } from "@playwright/test";
import { login, logout, submit, confirm, pickup } from "./helpers";
const test = base.extend<{ noApi: void }>({
  noApi: [
    async ({ page }, use) => {
      const requests: string[] = [];
      const errors: string[] = [];
      page.on("request", (r) => {
        if (/^\/(api|actuator)(\/|$)/.test(new URL(r.url()).pathname))
          requests.push(r.url());
      });
      page.on("pageerror", (e) => errors.push(e.message));
      await use();
      expect(requests, "Fixture must never call live API").toEqual([]);
      expect(errors, "Browser runtime errors").toEqual([]);
    },
    { auto: true },
  ],
});

test("Login load and refresh leave fields untouched and preserve keyboard focus", async ({
  page,
}) => {
  await page.goto("/login");
  for (const refresh of [false, true]) {
    if (refresh) await page.reload();
    await expect(page.locator("main")).toBeFocused();
    await expect(page.locator("main")).toHaveCSS("outline-style", "none");
    await expect(page.getByLabel("Username", { exact: true })).not.toHaveClass(
      /ng-touched/,
    );
    await expect(page.getByLabel("Password", { exact: true })).not.toHaveClass(
      /ng-touched/,
    );
    await page.keyboard.press("Tab");
    await expect(page.getByLabel("Username", { exact: true })).toBeFocused();
    await expect(page.getByLabel("Username", { exact: true })).toHaveCSS(
      "outline-style",
      "solid",
    );
    await page.keyboard.press("Tab");
    await expect(page.getByLabel("Password", { exact: true })).toBeFocused();
    await expect(page.getByLabel("Username", { exact: true })).toHaveClass(
      /ng-touched/,
    );
  }
});

test("U1/U2: keyboard intake and complete cross-role handoff with private assessment", async ({
  page,
}) => {
  await login(page, "aina");
  await page
    .getByRole("link", { name: "Report an incident", exact: true })
    .first()
    .focus();
  await page.keyboard.press("Enter");
  await page.getByRole("button", { name: "Submit claim" }).click();
  await expect(
    page.getByText("Describe what happened before submitting."),
  ).toBeVisible();
  await page.getByLabel("Incident date", { exact: true }).fill("2099-01-01");
  await page.getByLabel("What happened?").fill("Fixture bumper incident");
  await page.getByRole("button", { name: "Submit claim" }).click();
  await expect(
    page.getByText(
      "Incident date cannot be in the future for the selected market.",
    ),
  ).toBeVisible();
  await page.getByLabel("Incident date", { exact: true }).fill("2026-11-18");
  await page.getByLabel("What happened?").fill("Fixture bumper incident");
  await page.getByRole("button", { name: "Submit claim" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(/^CL-/);
  const id = page.url().split("/").pop()!,
    ref = (await page.getByRole("heading", { level: 1 }).textContent())!;
  await page.getByRole("link", { name: "← My claims", exact: true }).click();
  await expect(
    page.getByRole("link", { name: ref, exact: true }),
  ).toBeVisible();
  await logout(page);
  await login(page, "farah");
  await pickup(page, id);
  await page.screenshot({
    path: "test-results/fixture-desktop-case.png",
    fullPage: true,
  });
  await page
    .getByLabel("Question for claimant")
    .fill("Please share the workshop estimate.");
  await page.getByRole("button", { name: "Send question" }).click();
  await expect(page.locator(".badge.large")).toHaveText("Awaiting information");
  await logout(page);
  await login(page, "aina");
  await page.goto("/my-claims/" + id);
  await page.getByLabel("Your reply").fill("Initial estimate is MYR 5,000.");
  await page.getByRole("button", { name: "Send reply" }).click();
  await expect(page.locator(".badge.large")).toHaveText("Under review");
  await logout(page);
  await login(page, "farah");
  await page.goto("/staff/claims/" + id);
  await expect(
    page.getByText("Initial estimate is MYR 5,000.", { exact: true }).first(),
  ).toBeVisible();
  await expect(
    page.getByText("Awaiting estimate", { exact: true }),
  ).toBeVisible();
  await page.getByLabel("Reserve amount (MYR)").fill("0.00");
  await page.getByRole("button", { name: "Save assessment" }).click();
  await confirm(page, "assessment");
  await expect(page.locator(".amount")).toHaveText("MYR 0.00");
  await page.getByLabel("Reserve amount (MYR)").fill("5000.00");
  await page.getByRole("button", { name: "Save assessment" }).click();
  await confirm(page, "assessment");
  await expect(page.locator(".amount")).toHaveText("MYR 5000.00");
  await logout(page);
  await login(page, "aina");
  await page.goto("/my-claims/" + id);
  await expect(
    page.getByRole("heading", { name: "Claim history" }),
  ).toBeVisible();
  await expect(page.locator("main")).not.toContainText(
    /reserve|5000\.00|Staff only/i,
  );
  await logout(page);
  await login(page, "farah");
  await page.goto("/staff/claims/" + id);
  await page.getByLabel("Settlement amount (MYR)").fill("4200.00");
  await page.getByRole("button", { name: "Review settlement" }).click();
  await expect(page.getByRole("dialog")).toContainText("MYR 4200.00");
  await confirm(page, "settlement");
  await expect(page.locator(".badge.large")).toHaveText("Settled");
  await logout(page);
  await login(page, "aina");
  await page.goto("/my-claims/" + id);
  await expect(
    page.getByRole("heading", { name: "Settlement recorded" }),
  ).toBeVisible();
  await expect(page.locator(".amount")).toHaveText("MYR 4200.00");
  await expect(page.locator("main")).not.toContainText(/reserve|Staff only/i);
});

test("U2: rejected named fixture and new SG rejection", async ({ page }) => {
  await login(page, "aina");
  await page.getByRole("link", { name: "CL-102", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Claim rejected" }),
  ).toBeVisible();
  await expect(page.locator(".decision")).toContainText(
    "Duplicate incident confirmed by claimant.",
  );
  await logout(page);
  await login(page, "daniel");
  const c = await submit(page, "Duplicate property report", "SG");
  await logout(page);
  await login(page, "arjun");
  await pickup(page, c.id);
  await page
    .getByLabel("Rejection reason")
    .fill("Claimant confirmed this is a duplicate property report.");
  await page.getByRole("button", { name: "Review rejection" }).click();
  await confirm(page, "rejection");
  await expect(page.locator(".badge.large")).toHaveText("Rejected");
  await logout(page);
  await login(page, "daniel");
  await page.goto("/my-claims/" + c.id);
  await expect(page.locator(".decision")).toContainText(
    "Claimant confirmed this is a duplicate property report.",
  );
});

test("U3: empty, loading, retryable read failure, forbidden and missing case", async ({
  page,
}) => {
  await login(page, "aina");
  await page.goto("/my-claims?scenario=empty");
  await expect(
    page.getByRole("heading", { name: "No claims yet" }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Report an incident" }).last(),
  ).toBeVisible();
  await page.goto("/my-claims?scenario=loading");
  await expect(page.getByRole("status")).toHaveText("Loading your workspace…");
  await expect(
    page.getByRole("link", { name: "CL-101", exact: true }),
  ).toBeVisible();
  await page.goto("/my-claims?scenario=error");
  await expect(page.getByRole("alert")).toContainText(
    "temporarily unavailable",
  );
  await page.getByRole("button", { name: "Try again" }).click();
  await expect(
    page.getByRole("link", { name: "CL-101", exact: true }),
  ).toBeVisible();
  await page.goto("/my-claims?scenario=permission");
  await expect(page.getByRole("alert")).toContainText("do not have permission");
  await page.goto("/my-claims/00000000-0000-4000-8000-000000009999");
  await expect(page.getByRole("alert")).toContainText("could not be found");
});

test("U3/U4: stale money command refreshes and requires reconfirmation; keyboard dialog focus", async ({
  page,
}) => {
  await login(page, "farah");
  await pickup(page, "00000000-0000-4000-8000-000000000101");
  await page.goto(
    "/staff/claims/00000000-0000-4000-8000-000000000101?scenario=conflict",
  );
  await page.getByLabel("Settlement amount (MYR)").fill("4200.00");
  const trigger = page.getByRole("button", { name: "Review settlement" });
  await trigger.focus();
  await page.keyboard.press("Enter");
  await expect(
    page.getByRole("button", { name: "Cancel", exact: true }),
  ).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(trigger).toBeFocused();
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await page.keyboard.press("Enter");
  await page.keyboard.press("Tab");
  await expect(
    page.getByRole("button", { name: "Confirm settlement" }),
  ).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("status")).toContainText("This claim changed");
  await expect(page.locator(".badge.large")).toHaveText("Under review");
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await trigger.click();
  await confirm(page, "settlement");
  await expect(page.locator(".badge.large")).toHaveText("Settled");
});

test("U4: 375px queue and action layout without page overflow", async ({
  page,
}) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await login(page, "farah");
  await expect(
    page.getByRole("link", { name: "CL-101", exact: true }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: "test-results/fixture-mobile-queue.png",
    fullPage: true,
  });
  await pickup(page, "00000000-0000-4000-8000-000000000101");
  const button = page.getByRole("button", { name: "Review settlement" });
  await button.scrollIntoViewIfNeeded();
  const box = await button.boundingBox();
  expect(box!.x).toBeGreaterThanOrEqual(0);
  expect(box!.x + box!.width).toBeLessThanOrEqual(375);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: "test-results/fixture-mobile-case.png",
    fullPage: true,
  });
});

test("Fixture reports release the full reserve after settlement", async ({
  page,
}) => {
  await login(page, "farah");
  await pickup(page, "00000000-0000-4000-8000-000000000101");
  await page.getByLabel("Reserve amount (MYR)").fill("5000.00");
  await page.getByRole("button", { name: "Save assessment" }).click();
  await confirm(page, "assessment");
  await expect(page.locator(".amount")).toHaveText("MYR 5000.00");
  await page.getByRole("link", { name: "Reports", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Workload & exposure" }),
  ).toBeVisible();
  const myr = page
    .getByRole("row")
    .filter({ has: page.getByRole("rowheader", { name: "MYR", exact: true }) });
  await expect(myr).toContainText("5000.00");
  await expect(myr.getByRole("cell")).toHaveText(["1", "0", "5000.00"]);
  await page.screenshot({
    path: "test-results/fixture-desktop-reports.png",
    fullPage: true,
  });
  await page.goto("/staff/claims/00000000-0000-4000-8000-000000000101");
  await page.getByLabel("Settlement amount (MYR)").fill("4200.00");
  await page.getByRole("button", { name: "Review settlement" }).click();
  await confirm(page, "settlement");
  await expect(page.locator(".badge.large")).toHaveText("Settled");
  await page.getByRole("link", { name: "Reports", exact: true }).click();
  await expect(page.getByText("No open exposure to report.")).toBeVisible();
  await expect(page.locator(".officers")).toContainText("Arjun");
});
