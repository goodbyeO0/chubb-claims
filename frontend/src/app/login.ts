import { Component, inject } from "@angular/core";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { Router } from "@angular/router";
import { PageState } from "./shared";
@Component({
  selector: "app-login",
  imports: [ReactiveFormsModule],
  template: ` <div class="login-layout">
    <section class="login-context">
      <p class="eyebrow">CLAIMS WORKBENCH</p>
      <h1>Every claim.<br />A clear next step.</h1>
      <p>
        Report an incident, follow its progress and stay in touch with your
        claims officer.
      </p>
      <ol class="steps">
        <li><span>01</span> Tell us what happened</li>
        <li><span>02</span> Work through the details</li>
        <li><span>03</span> Receive a clear decision</li>
      </ol>
    </section>
    <section class="panel login-form">
      <p class="eyebrow">YOUR WORKSPACE</p>
      <h2>Sign in</h2>
      <p class="muted">Use your workbench account to continue.</p>
      <form [formGroup]="form" (ngSubmit)="login()">
        <label for="username">Username</label
        ><input
          id="username"
          autocomplete="username"
          formControlName="username"
          required
        />
        <label for="password">Password</label
        ><input
          id="password"
          type="password"
          autocomplete="current-password"
          formControlName="password"
          required
        />
        @if (error()) {
          <p role="alert" class="field-error">{{ error() }}</p>
        }
        <button class="primary full" [disabled]="busy()">
          {{ busy() ? "Signing in…" : "Sign in" }}
        </button>
      </form>
      @if (api.fixture) {
        <aside class="sample">
          <strong>Sample workspace</strong>
          <p>
            Claimants: aina, daniel<br />Officers: farah, arjun<br />Password:
            local-demo-only
          </p>
          <p>Fictional data stored only in this browser.</p>
        </aside>
      }
    </section>
  </div>`,
})
export class LoginPage extends PageState {
  private router = inject(Router);
  private fb = inject(FormBuilder);
  form = this.fb.nonNullable.group({
    username: ["", Validators.required],
    password: ["", Validators.required],
  });
  async login() {
    if (this.busy()) return;
    if (this.form.invalid) {
      this.error.set("Enter your username and password.");
      return;
    }
    this.busy.set(true);
    this.error.set("");
    try {
      const v = this.form.getRawValue();
      await this.api.login(v.username, v.password);
      await this.router.navigateByUrl(
        this.api.user()?.role === "OFFICER" ? "/staff/queue" : "/my-claims",
      );
    } catch {
      this.error.set(
        "Unable to sign in. Check your username and password, then try again.",
      );
    } finally {
      this.busy.set(false);
    }
  }
}
