import { Component, inject, signal } from "@angular/core";
import {
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from "@angular/router";
import { WorkbenchApi } from "./api";
@Component({
  selector: "app-root",
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: ` <a class="skip-link" href="#main">Skip to content</a>
    <header class="app-header">
      <a
        class="brand"
        [routerLink]="
          api.user()?.role === 'OFFICER' ? '/staff/queue' : '/my-claims'
        "
        ><span class="brand-mark" aria-hidden="true">C</span
        ><span>Claims<span class="brand-sub">WORKBENCH</span></span></a
      >
      @if (api.user(); as user) {
        <nav aria-label="Main navigation">
          @if (user.role === "OFFICER") {
            <a routerLink="/staff/queue" routerLinkActive="active">Work queue</a
            ><a routerLink="/staff/reports" routerLinkActive="active"
              >Reports</a
            >
          } @else {
            <a routerLink="/my-claims" routerLinkActive="active">My claims</a>
          }
        </nav>
        <div class="account">
          <span
            >{{ user.displayName }}
            <small>{{
              user.role === "OFFICER" ? "Officer" : "Claimant"
            }}</small></span
          ><button (click)="logout()" [disabled]="busy()">Sign out</button>
        </div>
      }
    </header>
    @if (api.fixture) {
      <div class="fixture-banner">Sample workspace · Fictional claims</div>
    }
    @if (error()) {
      <p class="notice error" role="alert">{{ error() }}</p>
    }
    <main id="main" tabindex="-1"><router-outlet /></main>
    <footer>
      Claims workbench <span>Clear information. Considered decisions.</span>
    </footer>`,
})
export class App {
  api = inject(WorkbenchApi);
  router = inject(Router);
  busy = signal(false);
  error = signal("");
  constructor() {
    this.router.events.subscribe((e) => {
      if (e instanceof NavigationEnd) {
        document.title = "Claims workbench";
        setTimeout(() => document.getElementById("main")?.focus(), 0);
      }
    });
  }
  async logout() {
    this.busy.set(true);
    try {
      await this.api.logout();
      await this.router.navigateByUrl("/login");
    } catch {
      this.error.set("Unable to sign out. Please try again.");
    } finally {
      this.busy.set(false);
    }
  }
}
