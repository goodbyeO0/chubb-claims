import { Component, inject, signal } from "@angular/core";
import { RouterLink } from "@angular/router";
import { ApiFailure, WorkbenchApi } from "./api";
@Component({
  selector: "app-state",
  imports: [RouterLink],
  inputs: ["loading", "error"],
  template: `
    @if (loading) {
      <p role="status" class="loading">Loading your workspace…</p>
    }
    @if (error) {
      <div class="notice error" role="alert">
        <strong>{{ error }}</strong>
        <p>Refresh or try again when ready.</p>
        <a routerLink="/login">Return to sign in</a>
      </div>
    }
  `,
})
export class StateComponent {
  loading = false;
  error = "";
}
export class PageState {
  readonly api = inject(WorkbenchApi);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal("");
  readonly notice = signal("");
  async read(work: () => Promise<void>) {
    this.loading.set(true);
    this.error.set("");
    try {
      await work();
    } catch (e) {
      this.error.set(this.describe(e));
    } finally {
      this.loading.set(false);
    }
  }
  describe(e: unknown) {
    if (e instanceof ApiFailure) {
      if (e.status === 401)
        return "Your session has ended. Sign in to continue.";
      if (e.status === 403)
        return "You do not have permission for this action. Refresh the page or sign in with the correct account.";
      if (e.status === 404)
        return "This claim could not be found. Return to your claim list.";
      if (e.status === 0)
        return "Unable to connect. Check your connection and try again.";
      return e.message;
    }
    return "Unable to complete the request. Please try again.";
  }
}
