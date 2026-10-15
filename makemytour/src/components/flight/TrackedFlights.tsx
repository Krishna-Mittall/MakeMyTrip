import { useCallback, useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { useRouter } from "next/router";
import { PlaneTakeoff } from "lucide-react";
import { getTrackedFlights } from "@/api/extra";
import { useEvents } from "@/hooks/useEvents";
import { fmtTime } from "@/lib/format";
import StatusChip from "./StatusChip";
import EtaCountdown from "./EtaCountdown";

/** The flights of the user with their live status and a dynamic ETA (used in the dashboard). */
const TrackedFlights = () => {
  const user = useSelector((state: any) => state.user.user);
  const router = useRouter();
  const [flights, setFlights] = useState<any[]>([]);
  const [loaded, setLoaded] = useState(false);
  const load = useCallback(() => {
    if (user?.id) getTrackedFlights(user.id).then((list) => { setFlights(list); setLoaded(true); }).catch(() => null);
  }, [user?.id]);
  useEffect(load, [load]);
  useEvents(["FLIGHT_STATUS"], load);
  useEffect(() => {
    const t = setInterval(load, 60000);
    return () => clearInterval(t);
  }, [load]);

  if (!loaded) return null;
  if (!flights.length) {
    return (
      <div className="bg-white rounded-xl shadow-lg p-4 sm:p-6">
        <h2 className="text-2xl font-bold flex items-center gap-2 mb-2">
          <PlaneTakeoff className="w-6 h-6 text-blue-600" /> My flights - live
        </h2>
        <p className="text-sm text-gray-600 mb-3">You are not tracking any flight yet. Flights you book are tracked automatically; you can also follow any flight to get delay, boarding and arrival-time alerts.</p>
        <button className="text-sm px-3 py-1.5 rounded-lg bg-blue-600 text-white" onClick={() => router.push("/flight-status")}>
          Track a flight
        </button>
      </div>
    );
  }
  return (
    <div className="bg-white rounded-xl shadow-lg p-4 sm:p-6">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-2xl font-bold flex items-center gap-2">
          <PlaneTakeoff className="w-6 h-6 text-blue-600" /> My flights - live
        </h2>
        <button className="text-sm text-blue-600 hover:underline" onClick={() => router.push("/flight-status")}>
          All flights
        </button>
      </div>
      <div className="space-y-3">
        {flights.map((f) => (
          <button
            key={f.flightId}
            onClick={() => router.push(`/flight-status?flight=${f.flightId}`)}
            className="w-full text-left border rounded-lg p-3 hover:shadow-md transition-shadow"
          >
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <div className="font-semibold">
                  {f.flightName} <span className="text-gray-500 font-normal">· {f.from} → {f.to}</span>
                </div>
                <div className="text-xs text-gray-500">
                  {fmtTime(f.estimatedDeparture)} → {fmtTime(f.estimatedArrival)}
                  {f.gate ? ` · Gate ${f.gate}` : ""}
                </div>
              </div>
              <StatusChip status={f.status} label={f.label} />
            </div>
            {f.status === "DELAYED" && f.reason && <p className="text-xs text-amber-800 mt-1">Reason: {f.reason}</p>}
            <div className="mt-2 text-sm font-medium">
              <EtaCountdown status={f} />
            </div>
            {(f.status === "IN_AIR" || f.status === "DEPARTED") && (
              <div className="h-1.5 bg-gray-100 rounded mt-2">
                <div className="h-1.5 bg-sky-500 rounded" style={{ width: `${f.progressPercent}%` }} />
              </div>
            )}
          </button>
        ))}
      </div>
    </div>
  );
};

export default TrackedFlights;
