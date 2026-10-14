import { useCallback, useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { adminAdvanceRefund, adminRefundAnalytics, adminRefunds, errorMessage } from "@/api/extra";
import { toast } from "@/lib/toast";
import { fmtDateTime, inr } from "@/lib/format";

const badge: Record<string, string> = {
  PENDING: "bg-amber-100 text-amber-800",
  PROCESSED: "bg-blue-100 text-blue-800",
  COMPLETED: "bg-green-100 text-green-800",
};

/** Admin view of all refunds: analytics, a list with filters, and a button to move a refund to the next stage. */
const RefundsAdmin = () => {
  const user = useSelector((state: any) => state.user.user);
  const [status, setStatus] = useState("");
  const [rows, setRows] = useState<any[]>([]);
  const [stats, setStats] = useState<any>(null);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    if (!user?.id) return;
    try {
      const [list, analytics] = await Promise.all([adminRefunds(user.id, status || undefined), adminRefundAnalytics(user.id)]);
      setRows(list);
      setStats(analytics);
      setError("");
    } catch (e) {
      setError(errorMessage(e));
    }
  }, [user?.id, status]);
  useEffect(() => {
    load();
    const t = setInterval(load, 30000);
    return () => clearInterval(t);
  }, [load]);

  const advance = async (id: string) => {
    try {
      await adminAdvanceRefund(id, user.id);
      load();
    } catch (e) {
      toast({ title: errorMessage(e), tone: "error" });
    }
  };

  if (!user) return <p className="text-gray-600">Please log in as an admin.</p>;
  if (error) return <p className="text-red-600">{error}</p>;
  const maxReason = stats ? Math.max(1, ...Object.values<number>(stats.byReason)) : 1;

  return (
    <div className="space-y-6">
      {stats && (
        <>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            <Stat label="Refund requests" value={stats.totalRequests} />
            <Stat label="Total refunded" value={inr(stats.totalRefunded)} />
            <Stat label="Average refund" value={`${stats.averageRefundPercent}%`} />
            <Stat label="Pending / processed" value={`${stats.byStatus.PENDING} / ${stats.byStatus.PROCESSED}`} />
          </div>
          <div>
            <h4 className="font-semibold mb-2 text-sm">Cancellation reasons</h4>
            <div className="space-y-1.5">
              {Object.entries<number>(stats.byReason).map(([reason, count]) => (
                <div key={reason} className="flex items-center gap-2 text-sm">
                  <span className="w-48 sm:w-64 truncate text-gray-600">{reason}</span>
                  <span className="flex-1 h-2 bg-gray-100 rounded">
                    <span className="block h-2 bg-red-400 rounded" style={{ width: `${(count / maxReason) * 100}%` }} />
                  </span>
                  <span className="w-6 text-right">{count}</span>
                </div>
              ))}
            </div>
          </div>
        </>
      )}

      <div className="flex items-center gap-2">
        <label className="text-sm text-gray-600">Status</label>
        <select className="border rounded-md px-2 py-1.5 text-sm bg-white" value={status} onChange={(e) => setStatus(e.target.value)}>
          <option value="">All</option>
          <option value="PENDING">Pending</option>
          <option value="PROCESSED">Processed</option>
          <option value="COMPLETED">Completed</option>
        </select>
      </div>
      <div className="overflow-x-auto border rounded-lg">
        <table className="w-full text-sm min-w-[720px]">
          <thead className="bg-gray-50 text-left text-gray-600">
            <tr>
              <th className="p-2">Refund</th>
              <th className="p-2">Customer</th>
              <th className="p-2">Booking</th>
              <th className="p-2 text-right">Amount</th>
              <th className="p-2">Reason</th>
              <th className="p-2">Status</th>
              <th className="p-2">Requested</th>
              <th className="p-2" />
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 && (
              <tr>
                <td colSpan={8} className="p-6 text-center text-gray-500">
                  No refunds yet.
                </td>
              </tr>
            )}
            {rows.map((r) => (
              <tr key={r.id} className="border-t">
                <td className="p-2 font-mono text-xs">{r.refundReference}</td>
                <td className="p-2">{r.userName}</td>
                <td className="p-2">
                  {r.title}
                  <div className="text-xs text-gray-500">{r.bookingReference}</div>
                </td>
                <td className="p-2 text-right">
                  {inr(r.refundAmount)}
                  <div className="text-xs text-gray-500">{r.refundPercent}% of {inr(r.originalAmount)}</div>
                </td>
                <td className="p-2">{r.reason}</td>
                <td className="p-2">
                  <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${badge[r.status]}`}>{r.status}</span>
                </td>
                <td className="p-2 whitespace-nowrap">{fmtDateTime(r.createdAt)}</td>
                <td className="p-2">
                  {r.status !== "COMPLETED" && (
                    <button onClick={() => advance(r.id)} className="text-xs px-2 py-1 rounded border hover:bg-gray-50 whitespace-nowrap">
                      Mark {r.status === "PENDING" ? "processed" : "completed"}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

const Stat = ({ label, value }: { label: string; value: any }) => (
  <div className="rounded-xl border bg-white p-3">
    <div className="text-xs text-gray-500">{label}</div>
    <div className="text-xl font-bold">{value}</div>
  </div>
);

export default RefundsAdmin;
