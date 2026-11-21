import { inject, Injectable } from "@angular/core";
import {
  HttpClient,
  HttpErrorResponse,
  HttpParams,
} from "@angular/common/http";
import { firstValueFrom } from "rxjs";
import {
  Action,
  ApiFailure,
  Claim,
  Command,
  Exposure,
  Scope,
  Submit,
  User,
  WorkbenchApi,
  Workload,
} from "./api";

@Injectable()
export class HttpApi extends WorkbenchApi {
  readonly fixture = false;
  private http = inject(HttpClient);
  private async request<T>(
    method: string,
    url: string,
    body?: unknown,
  ): Promise<T> {
    try {
      return await firstValueFrom(this.http.request<T>(method, url, { body }));
    } catch (e) {
      if (e instanceof HttpErrorResponse) {
        if (e.status === 401) this.user.set(null);
        const error = e.error;
        throw new ApiFailure(
          e.status,
          typeof error?.code === "string" ? error.code : "UNAVAILABLE",
          typeof error?.message === "string"
            ? error.message
            : "Unable to reach the service. Please try again.",
        );
      }
      throw e;
    }
  }
  private csrf() {
    return this.request<void>("GET", "/api/auth/csrf");
  }
  async bootstrap() {
    await this.csrf();
    try {
      this.user.set(await this.request<User>("GET", "/api/auth/me"));
    } catch (e) {
      if (!(e instanceof ApiFailure && e.status === 401)) throw e;
    }
  }
  async login(username: string, password: string) {
    await this.csrf();
    await this.request<void>(
      "POST",
      "/api/auth/login",
      new HttpParams().set("username", username).set("password", password),
    );
    await this.csrf();
    this.user.set(await this.request<User>("GET", "/api/auth/me"));
  }
  async logout() {
    await this.request<void>("POST", "/api/auth/logout", {});
    this.user.set(null);
    await this.csrf();
  }
  list(staff: boolean, scope: Scope = "ALL") {
    return this.request<Claim[]>(
      "GET",
      staff ? `/api/staff/claims?scope=${scope}` : "/api/claims",
    );
  }
  detail(id: string, staff: boolean) {
    return this.request<Claim>(
      "GET",
      `${staff ? "/api/staff/claims" : "/api/claims"}/${encodeURIComponent(id)}`,
    );
  }
  submit(body: Submit) {
    return this.request<Claim>("POST", "/api/claims", body);
  }
  command(id: string, action: Action, body: Command) {
    const { requestId, ...payload } = body;
    const path =
      action === "answer"
        ? `/api/claims/${encodeURIComponent(id)}/requests/${encodeURIComponent(requestId!)}/answer`
        : `/api/staff/claims/${encodeURIComponent(id)}/${action === "ask" ? "requests" : action}`;
    return this.request<Claim>("POST", path, payload);
  }
  async reports() {
    const [workload, exposure] = await Promise.all([
      this.request<Workload>("GET", "/api/staff/reports/workload"),
      this.request<Exposure>("GET", "/api/staff/reports/exposure"),
    ]);
    return { workload, exposure };
  }
}
