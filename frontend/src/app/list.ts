import { Component, inject, OnInit, signal } from "@angular/core";
import { DatePipe } from "@angular/common";
import { ActivatedRoute, RouterLink } from "@angular/router";
import { Claim, Scope, StaffClaim, statusLabel } from "./api";
import { PageState, StateComponent } from "./shared";
@Component({
  selector: "app-list",
  imports: [RouterLink, DatePipe, StateComponent],
  template: ` <div class="page-heading">
      <div>
        <p class="eyebrow">{{ staff ? "CLAIMS OPERATIONS" : "YOUR CLAIMS" }}</p>
        <h1>{{ staff ? "Work queue" : "My claims" }}</h1>
        <p class="muted">
          {{
            staff
              ? "Choose a case to review, or return to your assigned work."
              : "Track your reports and respond when we need more information."
          }}
        </p>
      </div>
      @if (!staff) {
        <a class="button primary" routerLink="/my-claims/new"
          >Report an incident</a
        >
      }
    </div>
    @if (staff) {
      <div class="toolbar">
        <div class="segmented" aria-label="Queue scope">
          @for (option of scopes; track option.value) {
            <button
              [attr.aria-pressed]="scope() === option.value"
              [disabled]="loading()"
              (click)="changeScope(option.value)"
            >
              {{ option.label }}
            </button>
          }
        </div>
        <button (click)="load()" [disabled]="loading()">Refresh queue</button>
      </div>
    }
    <app-state [loading]="loading()" [error]="error()" />
    @if (error()) {
      <button (click)="load()">Try again</button>
    }
    @if (!loading() && !error()) {
      @if (rows().length === 0) {
        <section class="empty panel">
          <h2>{{ staff ? "No cases in this view" : "No claims yet" }}</h2>
          <p>
            {{
              staff
                ? "Try All to see the rest of the team’s work."
                : "When you report an incident, you can follow every step here."
            }}
          </p>
          @if (!staff) {
            <a class="button primary" routerLink="/my-claims/new"
              >Report an incident</a
            >
          }
        </section>
      } @else {
        <p class="muted count">
          {{ rows().length }} {{ rows().length === 1 ? "case" : "cases" }}
        </p>
        <div class="table-wrap">
          <table class="claim-table">
            <thead>
              <tr>
                <th>Reference / incident</th>
                <th>Status</th>
                <th>Market</th>
                <th>{{ staff ? "Owner" : "Next step" }}</th>
                <th>Submitted</th>
              </tr>
            </thead>
            <tbody>
              @for (c of rows(); track c.id) {
                <tr>
                  <td>
                    <a
                      class="case-link"
                      [routerLink]="[
                        staff ? '/staff/claims' : '/my-claims',
                        c.id,
                      ]"
                      >{{ c.claimRef }}</a
                    ><span class="cell-sub"
                      >{{ c.kind === "MOTOR" ? "Motor" : "Property" }} ·
                      {{ c.incidentOn }}</span
                    >
                  </td>
                  <td data-label="Status">
                    <span
                      class="badge"
                      [class.waiting]="c.status === 'AWAITING_INFO'"
                      >{{ labels[c.status] }}</span
                    >
                  </td>
                  <td data-label="Market">{{ c.market }}</td>
                  <td [attr.data-label]="staff ? 'Owner' : 'Next step'">
                    {{ staff ? owner(c) : next(c) }}
                  </td>
                  <td data-label="Submitted">
                    {{ c.submittedAt | date: "dd MMM yyyy, HH:mm" }}
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    }`,
})
export class ListPage extends PageState implements OnInit {
  staff = inject(ActivatedRoute).snapshot.data["staff"] === true;
  rows = signal<Claim[]>([]);
  scope = signal<Scope>("ALL");
  labels = statusLabel;
  scopes: { value: Scope; label: string }[] = [
    { value: "ALL", label: "All" },
    { value: "MINE", label: "Mine" },
    { value: "UNASSIGNED", label: "Unassigned" },
  ];
  ngOnInit() {
    void this.load();
  }
  load() {
    return this.read(async () =>
      this.rows.set(await this.api.list(this.staff, this.scope())),
    );
  }
  changeScope(scope: Scope) {
    this.scope.set(scope);
    void this.load();
  }
  owner(c: Claim) {
    const id = (c as StaffClaim).officerId;
    return id === null
      ? "Unassigned"
      : id === this.api.user()?.id
        ? "You"
        : "Another officer";
  }
  next(c: Claim) {
    return c.status === "AWAITING_INFO"
      ? "Reply needed"
      : c.closedAt
        ? "View decision"
        : "We are reviewing";
  }
}
