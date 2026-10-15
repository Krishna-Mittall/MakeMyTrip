import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSelector } from "react-redux";
import { Armchair, Clock, Heart, RefreshCw, Sparkles, Wand2 } from "lucide-react";
import { errorMessage, getPreferences, getSeatMap, holdSeats, releaseSeats, savePreferences } from "@/api/extra";
import { useEvents } from "@/hooks/useEvents";
import { toast } from "@/lib/toast";
import { inr } from "@/lib/format";

type Props = {
  flightId: string;
  selected: string[];
  onChange: (seats: string[], extraCharge: number) => void;
  max?: number;
};

const tierStyle: Record<string, string> = {
  PREMIUM: "bg-amber-100 border-amber-400 text-amber-900 hover:bg-amber-200",
  EXTRA_LEGROOM: "bg-sky-100 border-sky-400 text-sky-900 hover:bg-sky-200",
  STANDARD: "bg-emerald-50 border-emerald-300 text-emerald-900 hover:bg-emerald-100",
};
const tierName: Record<string, string> = { PREMIUM: "Premium", EXTRA_LEGROOM: "Extra legroom", STANDARD: "Standard" };

/** Interactive seat map of a flight: live availability, premium seats, 5 minute holds and saved preferences. */
const SeatMap = ({ flightId, selected, onChange, max = 9 }: Props) => {
  const user = useSelector((state: any) => state.user.user);
  const [map, setMap] = useState<any>(null);
  const [error, setError] = useState("");
  const [prefs, setPrefs] = useState<any>({ seatPosition: "ANY", seatTier: "ANY" });
  const [now, setNow] = useState(Date.now());
  const selectedRef = useRef<string[]>(selected);
  selectedRef.current = selected;
  const busy = useRef(false);

  const load = useCallback(async () => {
    try {
      const data = await getSeatMap(flightId, user?.id);
      setMap(data);
      setError("");
      // drop seats that somebody else took in the meantime
      const taken = new Set(
        data.seats.filter((s: any) => s.status === "BOOKED" || s.status === "HELD").map((s: any) => s.seatNumber)
      );
      const kept = selectedRef.current.filter((s) => !taken.has(s));
      if (kept.length !== selectedRef.current.length) {
        toast({ title: "A selected seat was just taken", message: "Please pick another seat.", tone: "warning" });
        update(kept, data);
      }
    } catch (e) {
      setError(errorMessage(e));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [flightId, user?.id]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!user?.id) return;
    getPreferences(user.id).then(setPrefs).catch(() => null);
  }, [user?.id]);

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);

  // live: somebody booked or held a seat
  useEvents(["SEAT_UPDATE"], (e) => {
    if (e?.flightId === flightId && !busy.current) load();
  });

  // give the seats back when the page is left without booking
  useEffect(() => {
    return () => {
      if (user?.id) releaseSeats(flightId, user.id).catch(() => null);
    };
  }, [flightId, user?.id]);

  const chargeOf = (seats: string[], data = map) =>
    seats.reduce((sum, s) => sum + (data?.seats.find((x: any) => x.seatNumber === s)?.extraCharge || 0), 0);

  const update = (seats: string[], data = map) => onChange(seats, chargeOf(seats, data));

  const hold = async (seats: string[]) => {
    if (!user?.id) {
      update(seats);
      return;
    }
    busy.current = true;
    try {
      const data = seats.length ? await holdSeats(flightId, user.id, seats) : await releaseSeats(flightId, user.id);
      setMap(data);
      update(seats, data);
    } catch (e) {
      toast({ title: "Seat not available", message: errorMessage(e), tone: "error" });
      await load();
    } finally {
      busy.current = false;
    }
  };

  const toggle = (seat: any) => {
    if (seat.status === "BOOKED" || seat.status === "HELD") return;
    if (selected.includes(seat.seatNumber)) {
      hold(selected.filter((s) => s !== seat.seatNumber));
    } else if (selected.length >= max) {
      toast({ title: `You can select up to ${max} seats`, tone: "warning" });
    } else {
      hold([...selected, seat.seatNumber]);
    }
  };

  const rows = useMemo(() => {
    if (!map) return [];
    const byRow = new Map<number, any[]>();
    map.seats.forEach((s: any) => byRow.set(s.row, [...(byRow.get(s.row) || []), s]));
    return Array.from(byRow.entries()).sort((a, b) => a[0] - b[0]);
  }, [map]);

  const matches = (s: any) =>
    s.status === "AVAILABLE" &&
    (prefs.seatPosition === "ANY" || s.position === prefs.seatPosition) &&
    (prefs.seatTier === "ANY" || s.tier === prefs.seatTier);

  const pickPreferred = () => {
    if (!map) return;
    const found = map.seats.find((s: any) => matches(s) && !selected.includes(s.seatNumber));
    if (!found) {
      toast({ title: "No seat matches your preference", message: "Try another seat type.", tone: "warning" });
      return;
    }
    if (selected.length >= max) return;
    hold([...selected, found.seatNumber]);
  };

  const savePrefs = async (next: any) => {
    setPrefs(next);
    if (!user?.id) return;
    try {
      const saved = await savePreferences(user.id, next);
      setPrefs(saved);
      toast({ title: "Seat preference saved", message: "We will suggest it on your next booking.", tone: "success" });
    } catch (e) {
      toast({ title: "Could not save", message: errorMessage(e), tone: "error" });
    }
  };

  const saveFromSelection = () => {
    const seat = map?.seats.find((s: any) => s.seatNumber === selected[0]);
    if (seat) savePrefs({ ...prefs, seatPosition: seat.position, seatTier: seat.tier });
  };

  const heldUntil: number = map?.myHeldUntil || 0;
  const left = Math.max(0, Math.round((heldUntil - now) / 1000));
  useEffect(() => {
    if (heldUntil && now > heldUntil && selectedRef.current.length) {
      toast({ title: "Seat hold expired", message: "Please select your seats again.", tone: "warning" });
      onChange([], 0);
      load();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [now > heldUntil && heldUntil > 0]);

  if (error) {
    return <p className="text-sm text-red-600">{error}</p>;
  }
  if (!map) {
    return <div className="h-48 rounded-lg bg-gray-100 animate-pulse" />;
  }
  const columns: string[] = map.columns;
  const aisleAfter: string = map.aisleAfter;
  const seatButton = (s: any) => {
    const mine = selected.includes(s.seatNumber);
    const taken = s.status === "BOOKED";
    const held = s.status === "HELD";
    let cls = tierStyle[s.tier] || tierStyle.STANDARD;
    if (taken) cls = "bg-gray-200 border-gray-300 text-gray-400 cursor-not-allowed";
    else if (held) cls = "bg-orange-100 border-orange-300 text-orange-400 cursor-not-allowed";
    else if (mine) cls = "bg-red-600 border-red-700 text-white shadow";
    const suggested = !mine && !taken && !held && matches(s) && (prefs.seatPosition !== "ANY" || prefs.seatTier !== "ANY");
    return (
      <button
        key={s.seatNumber}
        type="button"
        disabled={taken || held}
        onClick={() => toggle(s)}
        aria-label={`Seat ${s.seatNumber}, ${tierName[s.tier]}, ${s.position.toLowerCase()}, ${taken ? "booked" : held ? "held" : "available"}`}
        title={`${s.seatNumber} · ${tierName[s.tier]} · ${s.position.toLowerCase()}${s.extraCharge ? ` · +${inr(s.extraCharge)}` : ""}`}
        className={`w-7 h-7 sm:w-8 sm:h-8 rounded-t-lg rounded-b-sm border text-[10px] sm:text-xs font-medium transition-colors ${cls} ${suggested ? "ring-2 ring-pink-400" : ""}`}
      >
        {taken ? "×" : s.column}
      </button>
    );
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2 text-sm text-gray-600">
          <Armchair className="w-4 h-4" />
          <span>
            {map.availableCount} of {map.capacity} seats free
          </span>
          <button onClick={load} className="text-blue-600 hover:text-blue-700" aria-label="Refresh seat map">
            <RefreshCw className="w-3.5 h-3.5" />
          </button>
        </div>
        {user && selected.length > 0 && left > 0 && (
          <div className="flex items-center gap-1 text-sm font-medium text-orange-600" role="timer">
            <Clock className="w-4 h-4" />
            Held for {Math.floor(left / 60)}:{String(left % 60).padStart(2, "0")}
          </div>
        )}
      </div>

      {/* saved preferences */}
      <div className="rounded-lg bg-gray-50 border p-3 space-y-2">
        <div className="flex flex-wrap items-end gap-3">
          <label className="text-xs text-gray-600">
            Preferred position
            <select
              className="block mt-1 border rounded-md px-2 py-1.5 text-sm bg-white"
              value={prefs.seatPosition || "ANY"}
              onChange={(e) => setPrefs({ ...prefs, seatPosition: e.target.value })}
            >
              <option value="ANY">Any</option>
              <option value="WINDOW">Window</option>
              <option value="MIDDLE">Middle</option>
              <option value="AISLE">Aisle</option>
            </select>
          </label>
          <label className="text-xs text-gray-600">
            Seat type
            <select
              className="block mt-1 border rounded-md px-2 py-1.5 text-sm bg-white"
              value={prefs.seatTier || "ANY"}
              onChange={(e) => setPrefs({ ...prefs, seatTier: e.target.value })}
            >
              <option value="ANY">Any</option>
              <option value="STANDARD">Standard</option>
              <option value="EXTRA_LEGROOM">Extra legroom</option>
              <option value="PREMIUM">Premium</option>
            </select>
          </label>
          <button
            type="button"
            onClick={pickPreferred}
            className="inline-flex items-center gap-1 text-sm px-3 py-1.5 rounded-md bg-pink-600 text-white hover:bg-pink-700"
          >
            <Wand2 className="w-4 h-4" /> Pick for me
          </button>
          {user && (
            <button
              type="button"
              onClick={() => savePrefs(prefs)}
              className="inline-flex items-center gap-1 text-sm px-3 py-1.5 rounded-md border bg-white hover:bg-gray-100"
            >
              <Heart className="w-4 h-4" /> Save preference
            </button>
          )}
          {user && selected.length > 0 && (
            <button type="button" onClick={saveFromSelection} className="text-sm text-blue-600 hover:underline">
              Use seat {selected[0]} as my preference
            </button>
          )}
        </div>
        {(prefs.seatPosition !== "ANY" || prefs.seatTier !== "ANY") && (
          <p className="text-xs text-gray-500">Seats that match your preference have a pink outline.</p>
        )}
      </div>

      {user && selected.length === 0 && (prefs.seatPosition !== "ANY" || prefs.seatTier !== "ANY") && (() => {
        const best = map.seats.find((s: any) => matches(s));
        if (!best) return null;
        return (
          <div className="rounded-lg border border-pink-300 bg-pink-50 p-3 text-sm flex flex-wrap items-center justify-between gap-2" data-testid="seat-suggestion">
            <span className="text-pink-900">
              <Heart className="w-4 h-4 inline mr-1" />
              Your saved preference ({[prefs.seatPosition !== "ANY" && prefs.seatPosition.toLowerCase(), prefs.seatTier !== "ANY" && tierName[prefs.seatTier].toLowerCase()].filter(Boolean).join(", ")}):
              seat <b>{best.seatNumber}</b> is free{best.extraCharge ? ` (+${inr(best.extraCharge)})` : ""}.
            </span>
            <button type="button" onClick={() => hold([best.seatNumber])} className="px-3 py-1 rounded-md bg-pink-600 text-white hover:bg-pink-700">
              Select seat {best.seatNumber}
            </button>
          </div>
        );
      })()}

      {/* legend */}
      <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-gray-600">
        {["PREMIUM", "EXTRA_LEGROOM", "STANDARD"].map((t) => (
          <span key={t} className="inline-flex items-center gap-1">
            <span className={`w-4 h-4 rounded-t border ${tierStyle[t].split(" hover")[0]}`} />
            {tierName[t]} {map.tierCharges[t] ? `(+${inr(map.tierCharges[t])})` : "(free)"}
          </span>
        ))}
        <span className="inline-flex items-center gap-1">
          <span className="w-4 h-4 rounded-t border bg-red-600 border-red-700" /> Your seat
        </span>
        <span className="inline-flex items-center gap-1">
          <span className="w-4 h-4 rounded-t border bg-gray-200 border-gray-300" /> Booked
        </span>
        <span className="inline-flex items-center gap-1">
          <span className="w-4 h-4 rounded-t border bg-orange-100 border-orange-300" /> Held by someone
        </span>
      </div>

      {/* the cabin */}
      <div className="mx-auto max-w-md rounded-t-[3rem] rounded-b-xl border-2 border-gray-300 bg-white px-3 sm:px-6 pt-8 pb-4">
        <div className="text-center text-xs text-gray-400 mb-3">FRONT</div>
        <div className="max-h-[26rem] overflow-y-auto pr-1">
          <div className="space-y-1.5">
            {rows.map(([row, seats]) => {
              const left = seats.filter((s: any) => columns.indexOf(s.column) <= columns.indexOf(aisleAfter));
              const right = seats.filter((s: any) => columns.indexOf(s.column) > columns.indexOf(aisleAfter));
              return (
                <div key={row} className="flex items-center justify-center gap-1 sm:gap-1.5">
                  <div className="flex gap-1 sm:gap-1.5">{left.map(seatButton)}</div>
                  <div className="w-6 text-center text-[10px] text-gray-400">{row}</div>
                  <div className="flex gap-1 sm:gap-1.5">{right.map(seatButton)}</div>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {selected.length > 0 && (
        <div className="rounded-lg border bg-white p-3 text-sm">
          <div className="flex flex-wrap items-center gap-2">
            <Sparkles className="w-4 h-4 text-red-500" />
            <span className="font-medium">Your seats:</span>
            {selected.map((s) => {
              const info = map.seats.find((x: any) => x.seatNumber === s);
              return (
                <span key={s} className="px-2 py-0.5 rounded-full bg-red-50 text-red-700 border border-red-200">
                  {s} {info?.extraCharge ? `(+${inr(info.extraCharge)})` : ""}
                </span>
              );
            })}
          </div>
          {chargeOf(selected) > 0 && (
            <p className="text-gray-600 mt-1">Seat upgrades add {inr(chargeOf(selected))} to your fare.</p>
          )}
        </div>
      )}
      {!user && <p className="text-xs text-gray-500">Log in to hold your seats for 5 minutes while you complete the booking.</p>}
    </div>
  );
};

export default SeatMap;
