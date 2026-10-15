import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/router";
import { useSelector } from "react-redux";
import { Bell, BellOff, Clock, MapPin, Plane, PlaneTakeoff, Radio, Search, Wrench } from "lucide-react";
import {
  errorMessage,
  getFlightBoard,
  getFlightStatus,
  getTrackedFlights,
  simulateFlight,
  demoAdvanceFlight,
  trackFlight,
  untrackFlight,
} from "@/api/extra";
import { useEvents } from "@/hooks/useEvents";
import { toast } from "@/lib/toast";
import { fmtDateTime, fmtTime } from "@/lib/format";
import StatusChip from "@/components/flight/StatusChip";
import EtaCountdown from "@/components/flight/EtaCountdown";

const REASONS = [
  "Air traffic congestion",
  "Bad weather at the destination",
  "Late arrival of the incoming aircraft",
  "Technical check of the aircraft",
  "Crew scheduling",
  "Runway maintenance",
];

const FlightStatusPage = () => {
  const router = useRouter();
  const user = useSelector((state: any) => state.user.user);
  const selectedId = typeof router.query.flight === "string" ? router.query.flight : null;
  const [board, setBoard] = useState<any[]>([]);
  const [tracked, setTracked] = useState<any[]>([]);
  const [detail, setDetail] = useState<any>(null);
  const [hours, setHours] = useState(24);
  const [q, setQ] = useState("");
  const [loading, setLoading] = useState(true);

  const loadBoard = useCallback(async () => {
    try {
      setBoard(await getFlightBoard(hours, q, user?.id));
    } catch (e) {
      toast({ title: "Could not load flights", message: errorMessage(e), tone: "error" });
    } finally {
      setLoading(false);
    }
  }, [hours, q, user?.id]);
  const loadTracked = useCallback(async () => {
    if (!user?.id) return setTracked([]);
    try {
      setTracked(await getTrackedFlights(user.id));
    } catch (e) {}
  }, [user?.id]);
  const loadDetail = useCallback(async () => {
    if (!selectedId) return setDetail(null);
    try {
      setDetail(await getFlightStatus(selectedId, user?.id));
    } catch (e) {
      setDetail(null);
    }
  }, [selectedId, user?.id]);

  useEffect(() => {
    const t = setTimeout(loadBoard, 250);
    return () => clearTimeout(t);
  }, [loadBoard]);
  useEffect(() => {
    loadTracked();
  }, [loadTracked]);
  useEffect(() => {
    loadDetail();
  }, [loadDetail]);

  // live updates from the backend
  useEvents(["FLIGHT_STATUS"], (s) => {
    const merge = (list: any[]) => list.map((f) => (f.flightId === s.flightId ? { ...f, ...s, tracked: f.tracked } : f));
    setBoard(merge);
    setTracked(merge);
    setDetail((d: any) => (d && d.flightId === s.flightId ? { ...d, ...s, tracked: d.tracked } : d));
  });
  useEffect(() => {
    const t = setInterval(() => {
      loadBoard();
      loadTracked();
      loadDetail();
    }, 60000);
    return () => clearInterval(t);
  }, [loadBoard, loadTracked, loadDetail]);

  const toggleTrack = async (f: any) => {
    if (!user?.id) {
      toast({ title: "Log in to track flights", message: "You will get a notification when the status changes.", tone: "warning" });
      return;
    }
    try {
      if (f.tracked) await untrackFlight(user.id, f.flightId);
      else await trackFlight(user.id, f.flightId);
      const flip = (list: any[]) => list.map((x) => (x.flightId === f.flightId ? { ...x, tracked: !f.tracked } : x));
      setBoard(flip);
      setDetail((d: any) => (d && d.flightId === f.flightId ? { ...d, tracked: !f.tracked } : d));
      loadTracked();
      toast({ title: f.tracked ? "Stopped tracking" : "Tracking this flight", message: f.tracked ? undefined : "We will notify you about every change.", tone: "success" });
    } catch (e) {
      toast({ title: errorMessage(e), tone: "error" });
    }
  };

  const open = (id: string) => router.push(`/flight-status?flight=${id}`, undefined, { shallow: true });

  return (
    <div className="min-h-screen bg-[#f4f7fa]">
      <div className="max-w-6xl mx-auto px-4 py-6 sm:py-8 space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="text-2xl sm:text-3xl font-bold flex items-center gap-2">
              <PlaneTakeoff className="w-7 h-7 text-blue-600" /> Live flight status
            </h1>
            <p className="text-gray-600 text-sm mt-1 flex items-center gap-1">
              <Radio className="w-4 h-4 text-green-600" /> Updates arrive live. Track flights to get a notification when they are delayed, boarding or landed.
            </p>
          </div>
        </div>

        {selectedId && detail && (
          <DetailCard
            f={detail}
            onTrack={() => toggleTrack(detail)}
            onClose={() => router.push("/flight-status", undefined, { shallow: true })}
            isAdmin={user?.role === "ADMIN"}
            adminId={user?.id}
            loggedIn={!!user?.id}
            onChanged={() => {
              loadDetail();
              loadBoard();
              loadTracked();
            }}
          />
        )}

        {user && tracked.length > 0 && (
          <section>
            <h2 className="text-lg font-semibold mb-3">My tracked flights</h2>
            <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {tracked.map((f) => (
                <FlightCard key={f.flightId} f={{ ...f, tracked: true }} onOpen={() => open(f.flightId)} onTrack={() => toggleTrack({ ...f, tracked: true })} />
              ))}
            </div>
          </section>
        )}

        <section>
          <div className="flex flex-wrap items-end justify-between gap-3 mb-3">
            <h2 className="text-lg font-semibold">Departures</h2>
            <div className="flex flex-wrap gap-2">
              <div className="relative">
                <Search className="w-4 h-4 absolute left-3 top-2.5 text-gray-400" />
                <input
                  value={q}
                  onChange={(e) => setQ(e.target.value)}
                  placeholder="Flight or city"
                  className="border rounded-lg pl-9 pr-3 py-2 text-sm bg-white w-44 sm:w-56"
                  aria-label="Search flights"
                />
              </div>
              <select value={hours} onChange={(e) => setHours(Number(e.target.value))} className="border rounded-lg px-3 py-2 text-sm bg-white" aria-label="Time window">
                <option value={6}>Next 6 hours</option>
                <option value={24}>Next 24 hours</option>
                <option value={72}>Next 3 days</option>
                <option value={336}>Next 2 weeks</option>
              </select>
            </div>
          </div>
          {loading ? (
            <div className="h-40 rounded-xl bg-white animate-pulse" />
          ) : board.length === 0 ? (
            <div className="bg-white rounded-xl p-8 text-center text-gray-500">No flights found for this time window.</div>
          ) : (
            <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {board.map((f) => (
                <FlightCard key={f.flightId} f={f} onOpen={() => open(f.flightId)} onTrack={() => toggleTrack(f)} />
              ))}
            </div>
          )}
        </section>
        <p className="text-xs text-gray-400 text-center">Flight data comes from a simulated flight-data API (MockSky) built for this project.</p>
      </div>
    </div>
  );
};

const FlightCard = ({ f, onOpen, onTrack }: { f: any; onOpen: () => void; onTrack: () => void }) => (
  <div className="bg-white rounded-xl shadow-sm border p-4 flex flex-col gap-2 hover:shadow-md transition-shadow">
    <div className="flex items-start justify-between gap-2">
      <button onClick={onOpen} className="text-left min-w-0">
        <div className="font-semibold truncate">{f.flightName}</div>
        <div className="text-sm text-gray-600 flex items-center gap-1">
          {f.from} <Plane className="w-3.5 h-3.5" /> {f.to}
        </div>
      </button>
      <button
        onClick={onTrack}
        aria-label={f.tracked ? "Stop tracking" : "Track this flight"}
        title={f.tracked ? "Stop tracking" : "Track this flight"}
        className={`p-2 rounded-full ${f.tracked ? "bg-blue-100 text-blue-700" : "bg-gray-100 text-gray-500 hover:bg-gray-200"}`}
      >
        {f.tracked ? <Bell className="w-4 h-4" /> : <BellOff className="w-4 h-4" />}
      </button>
    </div>
    <div className="flex items-center justify-between">
      <StatusChip status={f.status} label={f.label} />
      {f.gate && <span className="text-xs text-gray-500">Gate {f.gate} · {f.terminal}</span>}
    </div>
    <button onClick={onOpen} className="text-left">
      <div className="flex items-center justify-between text-sm">
        <div>
          <div className={`font-semibold ${f.delayMinutes > 0 ? "text-amber-700" : ""}`}>{fmtTime(f.estimatedDeparture)}</div>
          {f.delayMinutes > 0 && <div className="text-xs text-gray-400 line-through">{fmtTime(f.scheduledDeparture)}</div>}
        </div>
        <div className="flex-1 mx-3 border-t border-dashed relative">
          <Plane className="w-4 h-4 text-gray-400 absolute -top-2 left-1/2 -translate-x-1/2 bg-white" />
        </div>
        <div className="text-right">
          <div className="font-semibold">{fmtTime(f.estimatedArrival)}</div>
          {f.arrivalDelayMinutes > 0 && <div className="text-xs text-gray-400 line-through">{fmtTime(f.scheduledArrival)}</div>}
        </div>
      </div>
      <div className="text-xs text-gray-500 mt-1">{fmtDateTime(f.estimatedDeparture).split(",")[0]} · <EtaCountdown status={f} /></div>
    </button>
    {f.status === "DELAYED" && f.reason && <p className="text-xs text-amber-800 bg-amber-50 rounded px-2 py-1">{f.reason}</p>}
  </div>
);

const DetailCard = ({ f, onTrack, onClose, isAdmin, adminId, loggedIn, onChanged }: any) => {
  const [minutes, setMinutes] = useState(60);
  const [reason, setReason] = useState(REASONS[0]);
  const [busy, setBusy] = useState(false);
  const run = async (action: string, mins?: number) => {
    setBusy(true);
    try {
      await simulateFlight(f.flightId, adminId, action, mins, action === "DELAY" || action === "CANCEL" ? reason : undefined);
      onChanged();
    } catch (e) {
      toast({ title: errorMessage(e), tone: "error" });
    } finally {
      setBusy(false);
    }
  };
  const demo = async () => {
    setBusy(true);
    try {
      const wasTracked = f.tracked;
      if (!wasTracked) await trackFlight(adminId, f.flightId);
      await demoAdvanceFlight(f.flightId, adminId);
      if (!wasTracked) toast({ title: "Tracking this flight", message: "So that you get the notification for this demo update.", tone: "success" });
      onChanged();
    } catch (e) {
      toast({ title: errorMessage(e), tone: "error" });
    } finally {
      setBusy(false);
    }
  };
  const steps = useMemo(() => ["ON_TIME", "BOARDING", "DEPARTED", "IN_AIR", "LANDED"], []);
  const stepIndex = f.status === "DELAYED" ? 0 : Math.max(0, steps.indexOf(f.status));
  const stepLabel: Record<string, string> = { ON_TIME: "Scheduled", BOARDING: "Boarding", DEPARTED: "Departed", IN_AIR: "In the air", LANDED: "Landed" };

  return (
    <div className="bg-white rounded-2xl shadow-md border p-4 sm:p-6 space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-sm text-gray-500">{f.flightName}</div>
          <h2 className="text-2xl font-bold flex items-center gap-2 flex-wrap">
            {f.from} <Plane className="w-5 h-5" /> {f.to}
          </h2>
        </div>
        <div className="flex items-center gap-2">
          <StatusChip status={f.status} label={f.label} size="md" />
          <button onClick={onTrack} className={`inline-flex items-center gap-1 text-sm px-3 py-1.5 rounded-full border ${f.tracked ? "bg-blue-600 text-white border-blue-600" : "bg-white"}`}>
            {f.tracked ? <Bell className="w-4 h-4" /> : <BellOff className="w-4 h-4" />} {f.tracked ? "Tracking" : "Track"}
          </button>
          <button onClick={onClose} className="text-sm text-gray-500 underline">
            Close
          </button>
        </div>
      </div>

      <p className="text-gray-700">{f.message}</p>

      <div className="grid sm:grid-cols-3 gap-3">
        <div className="rounded-xl bg-gray-50 p-3">
          <div className="text-xs text-gray-500 flex items-center gap-1"><Clock className="w-3.5 h-3.5" /> Departure</div>
          <div className="text-xl font-bold">{fmtTime(f.estimatedDeparture)}</div>
          {f.delayMinutes > 0 ? <div className="text-xs text-gray-500">Scheduled <span className="line-through">{fmtTime(f.scheduledDeparture)}</span> · {fmtDateTime(f.estimatedDeparture)}</div> : <div className="text-xs text-gray-500">{fmtDateTime(f.scheduledDeparture)}</div>}
        </div>
        <div className="rounded-xl bg-gray-50 p-3">
          <div className="text-xs text-gray-500 flex items-center gap-1"><Clock className="w-3.5 h-3.5" /> Arrival (ETA)</div>
          <div className="text-xl font-bold">{fmtTime(f.estimatedArrival)}</div>
          {f.arrivalDelayMinutes > 0 ? <div className="text-xs text-gray-500">Scheduled <span className="line-through">{fmtTime(f.scheduledArrival)}</span></div> : <div className="text-xs text-gray-500">{fmtDateTime(f.scheduledArrival)}</div>}
        </div>
        <div className="rounded-xl bg-gray-50 p-3">
          <div className="text-xs text-gray-500 flex items-center gap-1"><MapPin className="w-3.5 h-3.5" /> Gate / Terminal</div>
          <div className="text-xl font-bold">{f.gate ? `${f.gate} · ${f.terminal}` : "To be announced"}</div>
          <div className="text-xs text-gray-500">Gate is shown 24 hours before departure</div>
        </div>
      </div>

      {f.status === "DELAYED" && f.reason && (
        <div className="rounded-xl bg-amber-50 border border-amber-200 p-3 text-sm text-amber-900">
          <b>Reason for the delay:</b> {f.reason}. New departure {fmtTime(f.estimatedDeparture)}, arrival {fmtTime(f.estimatedArrival)}.
        </div>
      )}
      {f.status === "CANCELLED" && (
        <div className="rounded-xl bg-red-50 border border-red-200 p-3 text-sm text-red-900">
          This flight is cancelled{f.reason ? ` (${f.reason})` : ""}. If you booked it, cancel the booking from your dashboard for a refund.
        </div>
      )}

      {/* progress */}
      <div>
        <div className="flex justify-between text-xs text-gray-500 mb-2">
          {steps.map((s, i) => (
            <span key={s} className={i <= stepIndex && f.status !== "CANCELLED" ? "font-semibold text-gray-900" : ""}>{stepLabel[s]}</span>
          ))}
        </div>
        <div className="h-2 rounded bg-gray-100 overflow-hidden">
          <div
            className="h-2 bg-blue-600 transition-all"
            style={{ width: `${f.status === "IN_AIR" || f.status === "DEPARTED" ? 60 + f.progressPercent * 0.3 : (stepIndex / (steps.length - 1)) * 100 + (f.status === "LANDED" ? 0 : 5)}%` }}
          />
        </div>
        <div className="text-sm font-medium mt-2 text-gray-700">
          <EtaCountdown status={f} />
        </div>
      </div>

      {/* what changed */}
      {f.history?.length > 0 && (
        <div>
          <h3 className="font-semibold mb-2">Updates</h3>
          <ul className="space-y-2">
            {f.history.map((h: any, i: number) => (
              <li key={i} className="flex gap-3 text-sm">
                <span className="text-gray-400 w-24 shrink-0">{fmtDateTime(h.at)}</span>
                <span className="text-gray-700">{h.message}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* demo of the live updates for everybody who is logged in */}
      <div className="rounded-xl border border-dashed border-blue-300 bg-blue-50/60 p-3 flex flex-wrap items-center justify-between gap-3">
        <div className="text-sm text-blue-900 min-w-0">
          <b>Try the live updates</b>
          <p className="text-xs text-blue-800/80">
            {loggedIn
              ? "Simulate the next update from the flight-data API (delay, boarding, departed, in the air, landed). You get a notification for it."
              : "Log in to simulate an update and see the notification, the bell and the revised arrival time."}
          </p>
        </div>
        {loggedIn && (
          <button disabled={busy} onClick={demo} className="px-3 py-1.5 rounded-md bg-blue-600 text-white text-sm disabled:opacity-60 whitespace-nowrap">
            Simulate next update
          </button>
        )}
      </div>

      {isAdmin && (
        <details className="rounded-xl border border-dashed p-3 bg-gray-50">
          <summary className="cursor-pointer text-sm font-medium flex items-center gap-2 inline-flex">
            <Wrench className="w-4 h-4" /> Mock flight API controls (admin)
          </summary>
          <p className="text-xs text-gray-500 mt-2">Change what the simulated flight-data provider reports for this flight to see the notifications and live updates.</p>
          <div className="flex flex-wrap items-end gap-2 mt-3">
            <label className="text-xs text-gray-600">
              Delay (min)
              <input type="number" min={5} step={5} value={minutes} onChange={(e) => setMinutes(Number(e.target.value))} className="block border rounded-md px-2 py-1.5 w-24 bg-white" />
            </label>
            <label className="text-xs text-gray-600">
              Reason
              <select value={reason} onChange={(e) => setReason(e.target.value)} className="block border rounded-md px-2 py-1.5 bg-white">
                {REASONS.map((r) => (
                  <option key={r}>{r}</option>
                ))}
              </select>
            </label>
            <button disabled={busy} onClick={() => run("DELAY", minutes)} className="px-3 py-1.5 rounded-md bg-amber-500 text-white text-sm">Delay</button>
            <button disabled={busy} onClick={() => run("ON_TIME")} className="px-3 py-1.5 rounded-md bg-green-600 text-white text-sm">On time</button>
            <button disabled={busy} onClick={() => run("CANCEL")} className="px-3 py-1.5 rounded-md bg-red-600 text-white text-sm">Cancel flight</button>
          </div>
          <div className="flex flex-wrap items-center gap-2 mt-3">
            <span className="text-xs text-gray-600">Fast-forward the flight:</span>
            {[30, 60, 180, 720].map((m) => (
              <button key={m} disabled={busy} onClick={() => run("SHIFT", m)} className="px-2.5 py-1 rounded-md border bg-white text-xs">
                +{m >= 60 ? `${m / 60}h` : `${m}m`}
              </button>
            ))}
            <button disabled={busy} onClick={() => run("CLEAR")} className="px-2.5 py-1 rounded-md border bg-white text-xs">
              Reset to automatic
            </button>
          </div>
        </details>
      )}
    </div>
  );
};

export default FlightStatusPage;
