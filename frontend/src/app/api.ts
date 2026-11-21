import { signal } from "@angular/core";
export type Role = "CLAIMANT" | "OFFICER";
export type Market = "MY" | "SG" | "TH" | "ID" | "PH" | "HK";
export type Status =
  "SUBMITTED" | "UNDER_REVIEW" | "AWAITING_INFO" | "SETTLED" | "REJECTED";
export interface User {
  id: string;
  displayName: string;
  role: Role;
}
export interface Submit {
  kind: "MOTOR" | "PROPERTY";
  market: Market;
  incidentOn: string;
  description: string;
}
export interface Info {
  id: string;
  question: string;
  askedAt: string;
  answer: string | null;
  answeredAt: string | null;
}
export interface ClaimEvent {
  type: string;
  at: string;
  message: string;
  actorId?: string;
  visibility?: string;
}
export interface Claim extends Submit {
  id: string;
  claimRef: string;
  currency: string;
  status: Status;
  version: number;
  submittedAt: string;
  closedAt: string | null;
  settlement: string | null;
  requests: Info[];
  timeline: ClaimEvent[];
}
export interface StaffClaim extends Claim {
  claimantId: string;
  officerId: string | null;
  reserve: string | null;
}
export type Scope = "ALL" | "MINE" | "UNASSIGNED";
export type Action =
  "assign" | "ask" | "answer" | "reserve" | "settle" | "reject";
export interface Command {
  expectedVersion: number;
  question?: string;
  answer?: string;
  amount?: string;
  reason?: string;
  requestId?: string;
}
export interface Workload {
  unassigned: number;
  awaitingInfo: number;
  closedLast7Days: number;
  openByOfficer: { officerId: string; displayName: string; count: number }[];
}
export interface Exposure {
  lastAppliedAt: string | null;
  byCurrency: {
    currency: string;
    openClaims: number;
    awaitingEstimate: number;
    knownOutstanding: string;
  }[];
}
export class ApiFailure extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}
export abstract class WorkbenchApi {
  readonly user = signal<User | null>(null);
  abstract readonly fixture: boolean;
  abstract bootstrap(): Promise<void>;
  abstract login(username: string, password: string): Promise<void>;
  abstract logout(): Promise<void>;
  abstract list(staff: boolean, scope?: Scope): Promise<Claim[]>;
  abstract detail(id: string, staff: boolean): Promise<Claim>;
  abstract submit(body: Submit): Promise<Claim>;
  abstract command(id: string, action: Action, body: Command): Promise<Claim>;
  abstract reports(): Promise<{ workload: Workload; exposure: Exposure }>;
}
export const statusLabel: Record<Status, string> = {
  SUBMITTED: "Submitted",
  UNDER_REVIEW: "Under review",
  AWAITING_INFO: "Awaiting information",
  SETTLED: "Settled",
  REJECTED: "Rejected",
};
export function publicClaim(c: StaffClaim): Claim {
  const { claimantId, officerId, reserve, ...safe } = structuredClone(c);
  safe.timeline = safe.timeline
    .filter((e) => e.visibility !== "STAFF_ONLY")
    .map(({ type, at, message }) => ({ type, at, message }));
  return safe;
}
