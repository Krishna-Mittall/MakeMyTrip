import { useCallback, useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { useRouter } from "next/router";
import { Lock, Snowflake } from "lucide-react";
import { cancelFreeze, errorMessage, getFreezes } from "@/api/extra";
import { toast } from "@/lib/toast";
import { fmtDateTime, inr, timeUntil } from "@/lib/format";

/** The price freezes of the user: the price that is held, for how long, and a shortcut to book. */
const PriceFreezes = () => {
  const user = useSelector((state: any) => state.user.user);
  const router = useRouter();
  const [list, setList] = useState<any[] | null>(null);
  const [tick, setTick] = useState(Date.now());

  const load = useCallback(() => {
    if (user?.id) getFreezes(user.id).then(setList).catch(() => setList([]));
  }, [user?.id]);
  useEffect(load, [load]);
  useEffect(() => {
    const t = setInterval(() => setTick(Date.now()), 30000);
    return () => clearInterval(t);
  }, []);

  if (!user || list === null) return null;
  const active = list.filter((f) => f.status === "ACTIVE" && new Date(f.expiresAt).getTime() > tick);
  const past = list.filter((f) => !active.includes(f)).slice(0, 3);

  const release = async (id: string) => {
    try {
      await cancelFreeze(id, user.id);
      load();
    } catch (e) {
      toast({ title: errorMessage(e), tone: "error" });
    }
  };
  const open = (f: any) => router.push(`/${f.itemType === "FLIGHT" ? "book-flight" : "book-hotel"}/${f.itemId}`);

  return (
    <div className="bg-white rounded-xl shadow-lg p-4 sm:p-6" data-testid="price-freezes">
      <h2 className="text-2xl font-bold flex items-center gap-2 mb-1">
        <Snowflake className="w-6 h-6 text-blue-600" /> My price freezes
      </h2>
      <p className="text-sm text-gray-600 mb-4">A frozen price is held for you for up to 48 hours. You pay the lower of the frozen and the current price.</p>
      {active.length === 0 ? (
        <p className="text-sm text-gray-500">No active price freeze. Open a flight or a hotel and press "Freeze this price".</p>
      ) : (
        <div className="space-y-3">
          {active.map((f) => (
            <div key={f.id} className="rounded-lg border border-blue-200 bg-blue-50 p-3 flex flex-wrap items-center justify-between gap-2">
              <div className="min-w-0">
                <div className="font-medium flex items-center gap-1.5">
                  <Lock className="w-4 h-4 text-blue-700" /> {f.itemName}
                </div>
                <div className="text-sm text-blue-900">
                  Frozen at <b>{inr(f.frozenPrice)}</b> {f.itemType === "FLIGHT" ? "per seat" : "per room / night"}
                  {f.stayDate ? ` · stay from ${f.stayDate}` : ""}
                </div>
                <div className="text-xs text-blue-800">Valid for {timeUntil(f.expiresAt, tick)} (until {fmtDateTime(f.expiresAt)})</div>
              </div>
              <div className="flex gap-2">
                <button onClick={() => open(f)} className="text-sm px-3 py-1.5 rounded-md bg-blue-600 text-white hover:bg-blue-700">Book now</button>
                <button onClick={() => release(f.id)} className="text-sm px-3 py-1.5 rounded-md border bg-white hover:bg-gray-50">Release</button>
              </div>
            </div>
          ))}
        </div>
      )}
      {past.length > 0 && (
        <details className="mt-3 text-sm text-gray-600">
          <summary className="cursor-pointer">Earlier freezes</summary>
          <ul className="mt-2 space-y-1">
            {past.map((f) => (
              <li key={f.id}>
                {f.itemName} · {inr(f.frozenPrice)} · {f.status === "USED" ? "used for a booking" : f.status.toLowerCase()}
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
};

export default PriceFreezes;
