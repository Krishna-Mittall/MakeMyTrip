import { AlertTriangle, CheckCircle2, Clock, DoorOpen, Plane, PlaneLanding, PlaneTakeoff, XCircle } from "lucide-react";

const styles: Record<string, { cls: string; icon: any }> = {
  ON_TIME: { cls: "bg-green-100 text-green-800", icon: CheckCircle2 },
  DELAYED: { cls: "bg-amber-100 text-amber-900", icon: AlertTriangle },
  BOARDING: { cls: "bg-blue-100 text-blue-800", icon: DoorOpen },
  DEPARTED: { cls: "bg-indigo-100 text-indigo-800", icon: PlaneTakeoff },
  IN_AIR: { cls: "bg-sky-100 text-sky-800", icon: Plane },
  LANDED: { cls: "bg-gray-200 text-gray-800", icon: PlaneLanding },
  CANCELLED: { cls: "bg-red-100 text-red-800", icon: XCircle },
};

export const statusLabel = (status: string, delayMinutes = 0) => {
  switch (status) {
    case "DELAYED": {
      const h = Math.floor(delayMinutes / 60);
      const m = delayMinutes % 60;
      return `Delayed by ${h ? `${h}h` : ""}${h && m ? " " : ""}${m ? `${m}m` : ""}`.trim();
    }
    case "BOARDING":
      return "Boarding";
    case "DEPARTED":
      return "Departed";
    case "IN_AIR":
      return "In the air";
    case "LANDED":
      return "Landed";
    case "CANCELLED":
      return "Cancelled";
    default:
      return "On Time";
  }
};

/** A coloured badge: "On Time", "Delayed by 1h", "Boarding" ... */
const StatusChip = ({ status, label, size = "sm" }: { status?: string; label?: string; size?: "sm" | "md" }) => {
  if (!status) return null;
  const s = styles[status] || styles.ON_TIME;
  const Icon = s.icon || Clock;
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full font-medium whitespace-nowrap ${s.cls} ${size === "md" ? "px-3 py-1 text-sm" : "px-2 py-0.5 text-xs"}`}
    >
      <Icon className={size === "md" ? "w-4 h-4" : "w-3 h-3"} />
      {label || statusLabel(status)}
    </span>
  );
};

export default StatusChip;
