import {
  Component,
  ElementRef,
  inject,
  OnInit,
  signal,
  viewChild,
} from "@angular/core";
import { DatePipe } from "@angular/common";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { ActivatedRoute, RouterLink } from "@angular/router";
import {
  Action,
  ApiFailure,
  Claim,
  Command,
  StaffClaim,
  statusLabel,
} from "./api";
import { PageState, StateComponent } from "./shared";
@Component({
  selector: "app-detail",
  imports: [DatePipe, RouterLink, ReactiveFormsModule, StateComponent],
  templateUrl: "./detail.html",
})
export class DetailPage extends PageState implements OnInit {
  private route = inject(ActivatedRoute);
  private fb = inject(FormBuilder);
  readonly staff = this.route.snapshot.data["staff"] === true;
  readonly id = this.route.snapshot.paramMap.get("id")!;
  claim = signal<Claim | null>(null);
  labels = statusLabel;
  confirmation = signal<"reserve" | "settle" | "reject" | null>(null);
  readonly dialog =
    viewChild<ElementRef<HTMLDialogElement>>("confirmationDialog");
  private trigger: HTMLElement | null = null;
  private confirmedBody: Command | null = null;
  text = this.fb.nonNullable.group({
    value: [
      "",
      [
        Validators.required,
        Validators.pattern(/\S/),
        Validators.maxLength(2000),
      ],
    ],
  });
  reserve = this.fb.nonNullable.group({
    amount: [
      "",
      [
        Validators.required,
        Validators.pattern(/^(0|[1-9][0-9]{0,15})\.[0-9]{2}$/),
      ],
    ],
  });
  settlement = this.fb.nonNullable.group({
    amount: [
      "",
      [
        Validators.required,
        Validators.pattern(/^(0|[1-9][0-9]{0,15})\.[0-9]{2}$/),
        Validators.pattern(/^(?!0\.00$)/),
      ],
    ],
  });
  rejection = this.fb.nonNullable.group({
    reason: [
      "",
      [
        Validators.required,
        Validators.pattern(/\S/),
        Validators.maxLength(2000),
      ],
    ],
  });
  ngOnInit() {
    void this.load();
  }
  load() {
    return this.read(async () => {
      this.claim.set(await this.api.detail(this.id, this.staff));
    });
  }
  staffClaim() {
    return this.claim() as StaffClaim;
  }
  mine() {
    return this.staff && this.staffClaim()?.officerId === this.api.user()?.id;
  }
  pending() {
    return this.claim()?.requests.find((r) => r.answer === null);
  }
  next() {
    const c = this.claim();
    if (!c) return "";
    switch (c.status) {
      case "SUBMITTED":
        return this.staff
          ? "Pick up this case to begin the review."
          : "Your claim is waiting for an officer to review it.";
      case "UNDER_REVIEW":
        return this.staff
          ? "Review the details, record an assessment, then decide when ready."
          : "Your officer is reviewing the details. No action is needed from you.";
      case "AWAITING_INFO":
        return this.staff
          ? "Waiting for the claimant’s reply before continuing the review."
          : "Your officer needs more information. Reply to the question below.";
      case "SETTLED":
        return "The claim is closed. The recorded settlement is shown below.";
      case "REJECTED":
        return "The claim is closed. The officer’s reason is shown below.";
    }
  }
  async send(action: Action, body: Partial<Command> = {}) {
    if (this.busy() || !this.claim()) return;
    await this.execute(action, {
      expectedVersion: this.claim()!.version,
      ...body,
    });
  }
  async execute(action: Action, body: Command) {
    this.busy.set(true);
    this.error.set("");
    this.notice.set("");
    try {
      this.claim.set(await this.api.command(this.id, action, body));
      this.text.reset();
      this.notice.set(
        action === "reserve"
          ? "Assessment saved. Reports may take a moment to update."
          : "Claim updated.",
      );
    } catch (e) {
      if (e instanceof ApiFailure && e.status === 409) {
        try {
          this.claim.set(await this.api.detail(this.id, this.staff));
          this.notice.set(
            "This claim changed. The latest details are shown. Review them and confirm your action again.",
          );
        } catch (refreshError) {
          this.error.set(this.describe(refreshError));
        }
      } else this.error.set(this.describe(e));
    } finally {
      this.busy.set(false);
    }
  }
  async sendText() {
    this.text.markAllAsTouched();
    if (this.text.invalid) return;
    await this.send(
      this.staff ? "ask" : "answer",
      this.staff
        ? { question: this.text.controls.value.value }
        : {
            answer: this.text.controls.value.value,
            requestId: this.pending()?.id,
          },
    );
  }
  openConfirm(action: "reserve" | "settle" | "reject", event: Event) {
    const form =
      action === "reserve"
        ? this.reserve
        : action === "settle"
          ? this.settlement
          : this.rejection;
    form.markAllAsTouched();
    if (form.invalid || !this.claim()) return;
    this.confirmedBody = {
      expectedVersion: this.claim()!.version,
      ...form.getRawValue(),
    };
    this.confirmation.set(action);
    this.trigger = event.currentTarget as HTMLElement;
    this.dialog()?.nativeElement.showModal();
  }
  confirmationValue() {
    return (
      this.confirmedBody?.reason ??
      `${this.claim()?.currency} ${this.confirmedBody?.amount}`
    );
  }
  cancel() {
    this.dialog()?.nativeElement.close();
    this.confirmation.set(null);
    this.trigger?.focus();
  }
  async confirm() {
    const action = this.confirmation(),
      body = this.confirmedBody;
    this.cancel();
    if (action && body) await this.execute(action, body);
  }
}
