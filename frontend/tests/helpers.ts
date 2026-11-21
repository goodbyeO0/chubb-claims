import { expect, Page } from "@playwright/test";
export async function login(page: Page, username: string) {
  await page.goto("/login");
  await page.getByLabel("Username", { exact: true }).fill(username);
  await page.getByLabel("Password", { exact: true }).fill("local-demo-only");
  await page.getByLabel("Password", { exact: true }).press("Enter");
  await expect(
    page.getByRole("heading", {
      level: 1,
      name:
        username === "aina" || username === "daniel"
          ? "My claims"
          : "Work queue",
    }),
  ).toBeVisible();
}
export async function logout(page: Page) {
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Sign in", exact: true }),
  ).toBeVisible();
}
export async function submit(
  page: Page,
  description = "Rear bumper damaged in a collision.",
  market = "MY",
) {
  await page
    .getByRole("link", { name: "Report an incident", exact: true })
    .first()
    .click();
  await page
    .getByLabel("Incident type")
    .selectOption(market === "SG" ? "PROPERTY" : "MOTOR");
  await page.getByLabel("Market", { exact: true }).selectOption(market);
  await page.getByLabel("Incident date", { exact: true }).fill("2026-11-18");
  await page.getByLabel("What happened?").fill(description);
  await page.getByRole("button", { name: "Submit claim", exact: true }).click();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(/^CL-/);
  return {
    id: page.url().split("/").pop()!,
    ref: (await page.getByRole("heading", { level: 1 }).textContent())!,
  };
}
export async function confirm(
  page: Page,
  action: "assessment" | "settlement" | "rejection",
) {
  await expect(page.getByRole("dialog")).toBeVisible();
  await page
    .getByRole("button", { name: `Confirm ${action}`, exact: true })
    .click();
  await expect(page.getByRole("dialog")).not.toBeVisible();
}
export async function pickup(page: Page, id: string) {
  await page.goto("/staff/claims/" + id);
  await page.getByRole("button", { name: "Pick up case" }).click();
  await expect(
    page.getByRole("heading", { name: "Assigned to you" }),
  ).toBeVisible();
}
