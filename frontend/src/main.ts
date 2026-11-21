import { inject, provideAppInitializer } from "@angular/core";
import { bootstrapApplication } from "@angular/platform-browser";
import { CanActivateFn, provideRouter, Router } from "@angular/router";
import { App } from "./app/app";
import { WorkbenchApi } from "./app/api";
import { ApiImplementation } from "./app/api-provider";
import { LoginPage } from "./app/login";
import { ListPage } from "./app/list";
import { NewPage } from "./app/new";
import { DetailPage } from "./app/detail";
import { ReportsPage } from "./app/reports";
const guard: CanActivateFn = (route) => {
  const u = inject(WorkbenchApi).user(),
    router = inject(Router);
  return !u
    ? router.parseUrl("/login")
    : route.data["staff"] === (u.role === "OFFICER")
      ? true
      : router.parseUrl(u.role === "OFFICER" ? "/staff/queue" : "/my-claims");
};
bootstrapApplication(App, {
  providers: [
    { provide: WorkbenchApi, useClass: ApiImplementation },
    provideAppInitializer(() => inject(WorkbenchApi).bootstrap()),
    provideRouter([
      { path: "login", component: LoginPage },
      {
        path: "my-claims/new",
        component: NewPage,
        canActivate: [guard],
        data: { staff: false },
      },
      {
        path: "my-claims/:id",
        component: DetailPage,
        canActivate: [guard],
        data: { staff: false },
      },
      {
        path: "my-claims",
        component: ListPage,
        canActivate: [guard],
        data: { staff: false },
      },
      {
        path: "staff/queue",
        component: ListPage,
        canActivate: [guard],
        data: { staff: true },
      },
      {
        path: "staff/claims/:id",
        component: DetailPage,
        canActivate: [guard],
        data: { staff: true },
      },
      {
        path: "staff/reports",
        component: ReportsPage,
        canActivate: [guard],
        data: { staff: true },
      },
      { path: "**", redirectTo: "my-claims" },
    ]),
  ],
}).catch(() => {
  document.body.textContent =
    "Unable to start the workbench. Refresh this page to try again.";
});
