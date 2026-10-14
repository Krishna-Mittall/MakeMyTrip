export const inr = (n: number | undefined | null) =>
  `₹ ${Math.round(Number(n || 0)).toLocaleString("en-IN")}`;

export const fmtDateTime = (iso?: string | null) =>
  iso
    ? new Date(iso).toLocaleString("en-IN", {
        day: "numeric",
        month: "short",
        hour: "numeric",
        minute: "2-digit",
        hour12: true,
      })
    : "-";

export const fmtDate = (iso?: string | null) =>
  iso ? new Date(iso).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }) : "-";

export const fmtTime = (iso?: string | null) =>
  iso ? new Date(iso).toLocaleTimeString("en-IN", { hour: "numeric", minute: "2-digit", hour12: true }) : "-";

/** "2h 15m", "45m" */
export const duration = (minutes: number) => {
  const m = Math.max(0, Math.round(minutes));
  const h = Math.floor(m / 60);
  const r = m % 60;
  if (h === 0) return `${r}m`;
  return r === 0 ? `${h}h` : `${h}h ${r}m`;
};

/** Counts down to a time, e.g. "in 3h 20m" / "2 days". */
export const timeUntil = (iso: string, now = Date.now()) => {
  const diff = Math.round((new Date(iso).getTime() - now) / 60000);
  if (diff <= 0) return "now";
  if (diff >= 48 * 60) return `${Math.floor(diff / 1440)} days`;
  return duration(diff);
};

export const ago = (iso: string) => {
  const diff = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
  if (diff < 1) return "just now";
  if (diff < 60) return `${diff} min ago`;
  if (diff < 1440) return `${Math.floor(diff / 60)} h ago`;
  return `${Math.floor(diff / 1440)} d ago`;
};
