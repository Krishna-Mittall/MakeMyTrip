import { useEffect, useState } from "react";
import { getRefundPolicy } from "@/api/extra";

const colors = ["bg-orange-500", "bg-green-500", "bg-lime-500", "bg-yellow-500", "bg-red-500"];

/** The refund policy of flights or hotels, as shown to the traveller before booking. */
const RefundPolicyBlock = ({ type }: { type: "flight" | "hotel" }) => {
  const [rows, setRows] = useState<any[]>([]);
  useEffect(() => {
    getRefundPolicy()
      .then((p) => setRows(p[type]))
      .catch(() => null);
  }, [type]);
  if (!rows.length) return null;
  return (
    <div className="bg-gray-50 p-4 sm:p-6 rounded-xl">
      <div className="flex h-2.5 rounded-full overflow-hidden mb-4">
        {rows.map((r, i) => (
          <div key={r.key || r.when} className={`flex-1 ${colors[i] || "bg-red-500"}`} />
        ))}
      </div>
      <ul className="space-y-2">
        {rows.map((r, i) => (
          <li key={r.key || r.when} className="flex items-center justify-between gap-3 text-sm">
            <span className="flex items-center gap-2 text-gray-700">
              <span className={`w-2.5 h-2.5 rounded-full shrink-0 ${colors[i] || "bg-red-500"}`} />
              {r.when}
            </span>
            <span className="font-semibold whitespace-nowrap">{r.refundPercent}% refund</span>
          </li>
        ))}
      </ul>
      <p className="text-xs text-gray-500 mt-3">
        The first row is checked first. If you cancel later than that, the
        time left before {type === "flight" ? "departure" : "check-in"} decides the refund. You can also cancel only some of the {type === "flight" ? "seats" : "rooms"} - the refund is worked out for just that part. Cancel from your
        dashboard and track the refund there.
      </p>
    </div>
  );
};

export default RefundPolicyBlock;
