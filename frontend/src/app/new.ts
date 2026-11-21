import { Component, inject } from "@angular/core";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { Router, RouterLink } from "@angular/router";
import { Market, Submit } from "./api";
import { PageState } from "./shared";
import { futureIncident } from "./validation";
@Component({
  selector: "app-new",
  imports: [ReactiveFormsModule, RouterLink],
  template: ` <a class="back" routerLink="/my-claims">← My claims</a>
    <div class="page-heading">
      <div>
        <p class="eyebrow">NEW CLAIM</p>
        <h1>Report an incident</h1>
        <p class="muted">
          Start with what you know. Your officer can ask for more details later.
        </p>
      </div>
    </div>
    <form
      class="panel form-panel"
      [formGroup]="form"
      (ngSubmit)="submit()"
      novalidate
    >
      <p class="muted">All fields are required.</p>
      <div class="form-grid">
        <div>
          <label for="kind">Incident type</label
          ><select id="kind" formControlName="kind">
            <option value="MOTOR">Motor</option>
            <option value="PROPERTY">Property</option>
          </select>
        </div>
        <div>
          <label for="market">Market</label
          ><select id="market" formControlName="market">
            @for (m of markets; track m.code) {
              <option [value]="m.code">{{ m.name }}</option>
            }
          </select>
        </div>
      </div>
      <label for="date">Incident date</label
      ><input
        id="date"
        type="date"
        formControlName="incidentOn"
        required
        aria-describedby="date-help"
      /><small id="date-help"
        >Use the local date of the incident. Future dates are not
        accepted.</small
      >
      @if (form.touched && form.hasError("futureDate")) {
        <p class="field-error">
          Incident date cannot be in the future for the selected market.
        </p>
      }
      @if (
        form.controls.incidentOn.touched && form.controls.incidentOn.invalid
      ) {
        <p class="field-error">Enter the incident date.</p>
      }
      <label for="description">What happened?</label
      ><textarea
        id="description"
        rows="5"
        formControlName="description"
        maxlength="2000"
        required
        aria-describedby="description-help"
      ></textarea
      ><small id="description-help"
        >Describe the incident in up to 2,000 characters. Avoid unnecessary
        personal details.</small
      >
      @if (
        form.controls.description.touched && form.controls.description.invalid
      ) {
        <p class="field-error">Describe what happened before submitting.</p>
      }
      @if (error()) {
        <p role="alert" class="field-error">{{ error() }}</p>
      }
      <div class="form-actions">
        <button class="primary" [disabled]="busy()">
          {{ busy() ? "Submitting…" : "Submit claim" }}</button
        ><a routerLink="/my-claims">Cancel</a>
      </div>
    </form>`,
})
export class NewPage extends PageState {
  private fb = inject(FormBuilder);
  private router = inject(Router);
  markets = [
    { code: "MY", name: "Malaysia" },
    { code: "SG", name: "Singapore" },
    { code: "TH", name: "Thailand" },
    { code: "ID", name: "Indonesia" },
    { code: "PH", name: "Philippines" },
    { code: "HK", name: "Hong Kong" },
  ];
  form = this.fb.nonNullable.group(
    {
      kind: ["MOTOR" as Submit["kind"], Validators.required],
      market: ["MY" as Market, Validators.required],
      incidentOn: ["", Validators.required],
      description: [
        "",
        [
          Validators.required,
          Validators.pattern(/\S/),
          Validators.maxLength(2000),
        ],
      ],
    },
    {
      validators: (group) =>
        futureIncident(
          group.get("incidentOn")!.value,
          group.get("market")!.value,
        )
          ? { futureDate: true }
          : null,
    },
  );
  async submit() {
    if (this.busy()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.busy.set(true);
    this.error.set("");
    try {
      const c = await this.api.submit(this.form.getRawValue());
      await this.router.navigate(["/my-claims", c.id]);
    } catch (e) {
      this.error.set(this.describe(e));
    } finally {
      this.busy.set(false);
    }
  }
}
