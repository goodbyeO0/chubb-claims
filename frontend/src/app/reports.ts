import { Component, OnInit, signal } from "@angular/core";
import { DatePipe } from "@angular/common";
import { Exposure, Workload } from "./api";
import { PageState, StateComponent } from "./shared";
@Component({
  selector: "app-reports",
  imports: [DatePipe, StateComponent],
  template: `
    <div class="page-heading">
      <div>
        <p class="eyebrow">TEAM OVERVIEW</p>
        <h1>Workload & exposure</h1>
        <p class="muted">
          Live case workload and the latest processed financial position.
        </p>
      </div>
      <button (click)="load()" [disabled]="loading()">Refresh reports</button>
    </div>
    <app-state [loading]="loading()" [error]="error()" />
    @if (workload(); as w) {
      <section aria-labelledby="workload-title">
        <h2 id="workload-title">Team workload</h2>
        <div class="metrics">
          <div>
            <span>Unassigned</span><strong>{{ w.unassigned }}</strong>
          </div>
          <div>
            <span>Waiting on claimant</span
            ><strong>{{ w.awaitingInfo }}</strong>
          </div>
          <div>
            <span>Closed in 7 days</span
            ><strong>{{ w.closedLast7Days }}</strong>
          </div>
        </div>
        <div class="panel">
          <h3>Open cases by officer</h3>
          <ul class="officers">
            @for (o of w.openByOfficer; track o.officerId) {
              <li>
                <span>{{ o.displayName }}</span
                ><strong>{{ o.count }}</strong>
              </li>
            }
          </ul>
        </div>
      </section>
    }
    @if (exposure(); as e) {
      <section class="exposure">
        <h2>Outstanding exposure</h2>
        <p class="muted">
          Known estimates are grouped by currency. Unassessed cases are counted
          separately. Recent changes may take a moment to appear.
        </p>
        <p class="freshness">
          Last financial update:
          {{
            e.lastAppliedAt
              ? (e.lastAppliedAt | date: "dd MMM yyyy, HH:mm:ss")
              : "No events processed yet"
          }}
        </p>
        @if (!e.byCurrency.length) {
          <p class="empty panel">No open exposure to report.</p>
        } @else {
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Currency</th>
                  <th>Open claims</th>
                  <th>Awaiting estimate</th>
                  <th>Known outstanding</th>
                </tr>
              </thead>
              <tbody>
                @for (c of e.byCurrency; track c.currency) {
                  <tr>
                    <th>{{ c.currency }}</th>
                    <td>{{ c.openClaims }}</td>
                    <td>{{ c.awaitingEstimate }}</td>
                    <td class="numeric">{{ c.knownOutstanding }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>
    }
  `,
})
export class ReportsPage extends PageState implements OnInit {
  workload = signal<Workload | null>(null);
  exposure = signal<Exposure | null>(null);
  ngOnInit() {
    void this.load();
  }
  load() {
    return this.read(async () => {
      const r = await this.api.reports();
      this.workload.set(r.workload);
      this.exposure.set(r.exposure);
    });
  }
}
