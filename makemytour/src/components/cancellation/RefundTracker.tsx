import { CheckCircle2, Circle, Clock, Loader2 } from "lucide-react";
import { fmtDateTime, inr } from "@/lib/format";

const STEPS = [
  { key: "PENDING", label: "Refund requested", expected: "createdAt" },
  { key: "PROCESSED", label: "Processed by us", expected: "expectedProcessedBy" },
  { key: "COMPLETED", label: "Credited to you", expected: "expectedCompletedBy" },
];
const order = ["PENDING", "PROCESSED", "COMPLETED"];

const badge: Record<string, string> = {
  PENDING: "bg-amber-100 text-amber-800",
  PROCESSED: "bg-blue-100 text-blue-800",
  COMPLETED: "bg-green-100 text-green-800",
};

/** Refund status tracker: pending → processed → completed, with the expected timelines. */
const RefundTracker = ({ refunds }: { refunds: any[] }) => {
  if (!refunds?.length) return null;
  return (
    <div className="bg-white rounded-xl shadow-lg p-4 sm:p-6">
      <h2 className="text-2xl font-bold mb-4">Refund tracker</h2>
      <div className="space-y-4">
        {refunds.map((r) => {
          const at = order.indexOf(r.status);
          return (
            <div key={r.id} className="border rounded-lg p-4">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <div className="font-semibold">
                    {r.title} <span className="text-gray-400 font-normal text-sm">{r.subtitle}</span>
                  </div>
                  <div className="text-xs text-gray-500">
                    {r.refundReference} · booking {r.bookingReference} · {r.quantityCancelled} {r.bookingType === "Flight" ? "seat(s)" : "room(s)"}
                    {r.cancelledSeats?.length ? ` (${r.cancelledSeats.join(", ")})` : ""}
                  </div>
                </div>
                <div className="text-right">
                  <div className="font-bold text-green-700">{inr(r.refundAmount)}</div>
                  <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${badge[r.status]}`}>{r.status}</span>
                </div>
              </div>
              <div className="text-xs text-gray-500 mt-1">
                {r.refundPercent}% refund · {r.reason}
              </div>
              <ol className="mt-4 grid grid-cols-3 gap-2">
                {STEPS.map((s, i) => {
                  const done = i <= at;
                  const current = i === at && r.status !== "COMPLETED";
                  const when = i === 0 ? r.createdAt : i === 1 ? r.processedAt || r.expectedProcessedBy : r.completedAt || r.expectedCompletedBy;
                  const isExpected = !(i === 0 ? r.createdAt : i === 1 ? r.processedAt : r.completedAt);
                  return (
                    <li key={s.key} className="text-center">
                      <div className="flex items-center">
                        <div className={`h-0.5 flex-1 ${i === 0 ? "invisible" : done ? "bg-green-500" : "bg-gray-200"}`} />
                        {done && !current ? (
                          <CheckCircle2 className="w-6 h-6 text-green-600 shrink-0" />
                        ) : current ? (
                          <Loader2 className="w-6 h-6 text-blue-600 animate-spin shrink-0" />
                        ) : (
                          <Circle className="w-6 h-6 text-gray-300 shrink-0" />
                        )}
                        <div className={`h-0.5 flex-1 ${i === STEPS.length - 1 ? "invisible" : i < at ? "bg-green-500" : "bg-gray-200"}`} />
                      </div>
                      <div className={`text-xs mt-1 font-medium ${done ? "text-gray-900" : "text-gray-400"}`}>{s.label}</div>
                      <div className="text-[11px] text-gray-500 flex items-center justify-center gap-1">
                        {isExpected && <Clock className="w-3 h-3" />}
                        {isExpected ? "Expected " : ""}
                        {fmtDateTime(when)}
                      </div>
                    </li>
                  );
                })}
              </ol>
              {r.timelineNote && <p className="text-xs text-gray-500 mt-3">{r.timelineNote}</p>}
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default RefundTracker;
