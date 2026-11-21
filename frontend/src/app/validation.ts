import { Market } from "./api";
const zones: Record<Market, string> = {
  MY: "Asia/Kuala_Lumpur",
  SG: "Asia/Singapore",
  TH: "Asia/Bangkok",
  ID: "Asia/Jakarta",
  PH: "Asia/Manila",
  HK: "Asia/Hong_Kong",
};
export function futureIncident(date: string, market: Market): boolean {
  const today = new Intl.DateTimeFormat("en-CA", {
    timeZone: zones[market],
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
  return date > today;
}
