import { Injectable } from "@angular/core";
import fixtures from "../../../api/ui-fixtures.json";
import {
  Action,
  ApiFailure,
  Claim,
  Command,
  Exposure,
  Market,
  publicClaim,
  Scope,
  StaffClaim,
  Submit,
  User,
  WorkbenchApi,
} from "./api";
import { futureIncident } from "./validation";
const key = "claims-fixture-v1";
const currency: Record<Market, string> = {
  MY: "MYR",
  SG: "SGD",
  TH: "THB",
  ID: "IDR",
  PH: "PHP",
  HK: "HKD",
};
const users: Record<string, User> = {
  aina: fixtures.me.claimant as User,
  farah: fixtures.me.officer as User,
  daniel: {
    ...fixtures.me.claimant,
    id: "00000000-0000-4000-8000-000000000002",
    displayName: "Daniel",
    role: "CLAIMANT",
  },
  arjun: {
    ...fixtures.me.officer,
    id: "00000000-0000-4000-8000-000000000011",
    displayName: "Arjun",
    role: "OFFICER",
  },
};
@Injectable()
export class FixtureApi extends WorkbenchApi {
  readonly fixture = true;
  private consumed = new Set<string>();
  private records(): StaffClaim[] {
    const saved = localStorage.getItem(key);
    if (saved) return JSON.parse(saved);
    const rows = structuredClone(fixtures.staffQueue.normal) as StaffClaim[];
    const rejected = structuredClone(fixtures.myClaim.rejected) as Claim;
    rows.push({
      ...rejected,
      id: "00000000-0000-4000-8000-000000000102",
      claimRef: "CL-102",
      claimantId: users["aina"].id,
      officerId: users["farah"].id,
      reserve: null,
    });
    this.save(rows);
    return rows;
  }
  private save(rows: StaffClaim[]) {
    localStorage.setItem(key, JSON.stringify(rows));
  }
  private async delay(read = false) {
    const scenario = new URLSearchParams(location.search).get("scenario");
    await new Promise((r) =>
      setTimeout(r, scenario === "loading" ? 1500 : 100),
    );
    if (read && scenario === "error" && !this.consumed.has("error")) {
      this.consumed.add("error");
      throw new ApiFailure(
        503,
        "UNAVAILABLE",
        "The service is temporarily unavailable.",
      );
    }
    if (read && scenario === "permission")
      throw new ApiFailure(
        403,
        "FORBIDDEN",
        "You do not have permission to view this page.",
      );
  }
  async bootstrap() {
    const username = sessionStorage.getItem("fixture-user");
    this.user.set(username ? (users[username] ?? null) : null);
  }
  async login(username: string, password: string) {
    await this.delay();
    if (!users[username] || password !== "local-demo-only")
      throw new ApiFailure(
        401,
        "BAD_CREDENTIALS",
        "Invalid username or password.",
      );
    sessionStorage.setItem("fixture-user", username);
    this.user.set(users[username]);
  }
  async logout() {
    sessionStorage.removeItem("fixture-user");
    this.user.set(null);
  }
  private identity() {
    const user = this.user();
    if (!user)
      throw new ApiFailure(401, "UNAUTHENTICATED", "Sign in to continue.");
    return user;
  }
  async list(staff: boolean, scope: Scope = "ALL") {
    await this.delay(true);
    const u = this.identity();
    if (staff && u.role !== "OFFICER")
      throw new ApiFailure(403, "FORBIDDEN", "Staff access required.");
    if (new URLSearchParams(location.search).get("scenario") === "empty")
      return structuredClone(fixtures.myClaims.empty);
    return this.records()
      .filter((c) =>
        staff
          ? scope === "ALL" ||
            (scope === "MINE" ? c.officerId === u.id : c.officerId === null)
          : c.claimantId === u.id,
      )
      .sort(
        (a, b) =>
          (staff ? 1 : -1) * a.submittedAt.localeCompare(b.submittedAt) ||
          a.id.localeCompare(b.id),
      )
      .map((c) => (staff ? c : publicClaim(c)));
  }
  async detail(id: string, staff: boolean) {
    await this.delay(true);
    const c = this.records().find((c) => c.id === id);
    const u = this.identity();
    if (staff && u.role !== "OFFICER")
      throw new ApiFailure(403, "FORBIDDEN", "Staff access required.");
    if (!c || (!staff && c.claimantId !== u.id))
      throw new ApiFailure(404, "NOT_FOUND", "Claim not found.");
    return staff ? c : publicClaim(c);
  }
  async submit(body: Submit) {
    await this.delay();
    const u = this.identity();
    if (u.role !== "CLAIMANT")
      throw new ApiFailure(403, "FORBIDDEN", "Claimant access required.");
    if (
      !body.description.trim() ||
      body.description.length > 2000 ||
      !body.incidentOn ||
      futureIncident(body.incidentOn, body.market)
    )
      throw new ApiFailure(
        400,
        "INVALID_INPUT",
        "Check the incident date and description.",
      );
    const rows = this.records();
    const now = new Date().toISOString();
    const c: StaffClaim = {
      ...(structuredClone(fixtures.submitClaim.submitted) as Claim),
      ...body,
      id: crypto.randomUUID(),
      claimRef: "CL-" + (1000 + rows.length),
      currency: currency[body.market],
      submittedAt: now,
      claimantId: u.id,
      officerId: null,
      reserve: null,
      requests: [],
      timeline: [
        {
          type: "CLAIM_SUBMITTED",
          at: now,
          message: "Claim received.",
          actorId: u.id,
          visibility: "CLAIMANT_VISIBLE",
        },
      ],
    };
    rows.push(c);
    this.save(rows);
    return publicClaim(c);
  }
  async command(id: string, action: Action, body: Command) {
    await this.delay();
    const u = this.identity();
    const rows = this.records();
    const c = rows.find((c) => c.id === id);
    if (!c) throw new ApiFailure(404, "NOT_FOUND", "Claim not found.");
    if (
      action === "answer"
        ? c.claimantId !== u.id
        : u.role !== "OFFICER" || (action !== "assign" && c.officerId !== u.id)
    )
      throw new ApiFailure(
        action === "answer" ? 404 : 403,
        "FORBIDDEN",
        "This action is not available to you.",
      );
    if (
      new URLSearchParams(location.search).get("scenario") === "conflict" &&
      !this.consumed.has("conflict")
    ) {
      this.consumed.add("conflict");
      c.version++;
      this.save(rows);
      throw new ApiFailure(409, "VERSION_CONFLICT", "This claim has changed.");
    }
    if (c.version !== body.expectedVersion)
      throw new ApiFailure(409, "VERSION_CONFLICT", "This claim has changed.");
    const allowed =
      action === "assign"
        ? c.status === "SUBMITTED" && c.officerId === null
        : action === "answer"
          ? c.status === "AWAITING_INFO"
          : c.status === "UNDER_REVIEW";
    if (!allowed)
      throw new ApiFailure(
        409,
        "INVALID_STATE",
        "This action is unavailable in the current status.",
      );
    const now = new Date().toISOString();
    let type = "",
      message = "";
    if (action === "assign") {
      c.officerId = u.id;
      c.status = "UNDER_REVIEW";
      type = "CLAIM_ASSIGNED";
      message = "An officer is reviewing your claim.";
    }
    if (action === "ask") {
      c.requests.push({
        id: crypto.randomUUID(),
        question: body.question!,
        askedAt: now,
        answer: null,
        answeredAt: null,
      });
      c.status = "AWAITING_INFO";
      type = "INFO_REQUESTED";
      message = body.question!;
    }
    if (action === "answer") {
      const request = c.requests.find(
        (r) => r.id === body.requestId && r.answer === null,
      );
      if (!request)
        throw new ApiFailure(404, "NOT_FOUND", "Request not found.");
      request.answer = body.answer!;
      request.answeredAt = now;
      c.status = "UNDER_REVIEW";
      type = "INFO_PROVIDED";
      message = body.answer!;
    }
    if (action === "reserve") {
      c.reserve = body.amount!;
      type = "RESERVE_SET";
      message = "Reserve set to " + c.currency + " " + body.amount + ".";
    }
    if (action === "settle") {
      c.status = "SETTLED";
      c.closedAt = now;
      c.settlement = body.amount!;
      c.reserve = "0.00";
      type = "CLAIM_SETTLED";
      message = "Claim settled for " + c.currency + " " + body.amount + ".";
    }
    if (action === "reject") {
      c.status = "REJECTED";
      c.closedAt = now;
      c.reserve = c.reserve === null ? null : "0.00";
      type = "CLAIM_REJECTED";
      message = body.reason!;
    }
    c.version++;
    c.timeline.push({
      type,
      at: now,
      message,
      actorId: u.id,
      visibility: action === "reserve" ? "STAFF_ONLY" : "CLAIMANT_VISIBLE",
    });
    this.save(rows);
    return u.role === "OFFICER" ? c : publicClaim(c);
  }
  async reports() {
    await this.delay(true);
    if (this.identity().role !== "OFFICER")
      throw new ApiFailure(403, "FORBIDDEN", "Staff access required.");
    const rows = this.records(),
      open = rows.filter((c) => !c.closedAt);
    const byCurrency: Exposure["byCurrency"] = [];
    for (const curr of [...new Set(open.map((c) => c.currency))].sort()) {
      const group = open.filter((c) => c.currency === curr);
      const cents = group.reduce(
        (sum, c) => sum + BigInt((c.reserve ?? "0.00").replace(".", "")),
        0n,
      );
      byCurrency.push({
        currency: curr,
        openClaims: group.length,
        awaitingEstimate: group.filter((c) => c.reserve === null).length,
        knownOutstanding: `${cents / 100n}.${String(cents % 100n).padStart(2, "0")}`,
      });
    }
    return {
      workload: {
        unassigned: open.filter((c) => !c.officerId).length,
        awaitingInfo: open.filter((c) => c.status === "AWAITING_INFO").length,
        closedLast7Days: rows.filter(
          (c) =>
            c.closedAt && Date.parse(c.closedAt) >= Date.now() - 7 * 86400000,
        ).length,
        openByOfficer: [users["farah"], users["arjun"]].map((u) => ({
          officerId: u.id,
          displayName: u.displayName,
          count: open.filter((c) => c.officerId === u.id).length,
        })),
      },
      exposure: {
        lastAppliedAt: rows.length ? new Date().toISOString() : null,
        byCurrency,
      },
    };
  }
}
