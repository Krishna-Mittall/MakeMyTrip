import { useCallback, useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { ArrowDown, ArrowUp, Lock, Minus, Snowflake, TrendingUp, Unlock } from "lucide-react";
import { cancelFreeze, createFreeze, errorMessage, getFlightQuote, getHotelQuote, getPriceHistory } from "@/api/extra";
import { useEvents } from "@/hooks/useEvents";
import { toast } from "@/lib/toast";
import { fmtDateTime, inr, timeUntil } from "@/lib/format";
import PriceChart from "./PriceChart";

type Props = {
  type: "FLIGHT" | "HOTEL";
  itemId: string;
  stayDate?: string;                 // hotels: the check-in date the price is shown for
  onQuote?: (info: { unit: number; current: number; frozen: boolean; freezeId?: string; saving: number }) => void;
  compact?: boolean;
};

const factorColor = (code: string, percent: number) =>
  percent > 0 ? (code === "HOLIDAY" || code === "PEAK" ? "bg-orange-500" : "bg-red-400") : "bg-green-500";

/** Live price, why it is what it is, the price history graph and the "price freeze". */
const PriceInsight = ({ type, itemId, stayDate, onQuote, compact }: Props) => {
  const user = useSelector((state: any) => state.user.user);
  const [quote, setQuote] = useState<any>(null);
  const [history, setHistory] = useState<any>(null);
  const [days, setDays] = useState(30);
  const [hours, setHours] = useState(24);
  const [busy, setBusy] = useState(false);
  const [flash, setFlash] = useState(false);
  const [tick, setTick] = useState(Date.now());

  const loadQuote = useCallback(async () => {
    try {
      const q = type === "FLIGHT" ? await getFlightQuote(itemId, user?.id) : await getHotelQuote(itemId, stayDate, user?.id);
      setQuote((old: any) => {
        if (old && old.currentPrice !== q.currentPrice) {
          setFlash(true);
          setTimeout(() => setFlash(false), 1500);
        }
        return q;
      });
    } catch (e) {}
  }, [type, itemId, stayDate, user?.id]);

  const loadHistory = useCallback(async () => {
    try {
      setHistory(await getPriceHistory(type, itemId, days));
    } catch (e) {}
  }, [type, itemId, days]);

  useEffect(() => {
    loadQuote();
  }, [loadQuote]);
  useEffect(() => {
    loadHistory();
  }, [loadHistory]);
  useEffect(() => {
    const t = setInterval(() => setTick(Date.now()), 30000);
    return () => clearInterval(t);
  }, []);

  // real-time price updates pushed by the pricing engine
  useEvents(["PRICE_UPDATE"], (changes) => {
    if (Array.isArray(changes) && changes.some((c) => c.type === type && c.id === itemId)) {
      loadQuote();
      loadHistory();
    }
  });

  useEffect(() => {
    if (!quote) return;
    const freeze = quote.freeze && quote.freeze.status === "ACTIVE" ? quote.freeze : null;
    const unit = freeze ? Math.min(freeze.frozenPrice, quote.currentPrice) : quote.currentPrice;
    onQuote?.({
      unit,
      current: quote.currentPrice,
      frozen: !!freeze,
      freezeId: freeze?.id,
      saving: freeze ? Math.max(0, quote.currentPrice - unit) : 0,
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [quote]);

  const freeze = quote?.freeze && quote.freeze.status === "ACTIVE" ? quote.freeze : null;

  const doFreeze = async () => {
    if (!user?.id) {
      toast({ title: "Please log in to freeze a price", tone: "warning" });
      return;
    }
    setBusy(true);
    try {
      await createFreeze(user.id, type, itemId, hours, stayDate);
      await loadQuote();
      toast({ title: "Price frozen", message: `We will hold this price for ${hours} hours.`, tone: "success" });
    } catch (e) {
      toast({ title: "Could not freeze the price", message: errorMessage(e), tone: "error" });
    } finally {
      setBusy(false);
    }
  };
  const release = async () => {
    if (!freeze) return;
    setBusy(true);
    try {
      await cancelFreeze(freeze.id, user.id);
      await loadQuote();
    } catch (e) {
      toast({ title: "Could not release", message: errorMessage(e), tone: "error" });
    } finally {
      setBusy(false);
    }
  };

  if (!quote) return <div className="h-40 rounded-xl bg-gray-100 animate-pulse" />;

  const TrendIcon = quote.trend === "UP" ? ArrowUp : quote.trend === "DOWN" ? ArrowDown : Minus;
  const trendColor = quote.trend === "UP" ? "text-red-600 bg-red-50" : quote.trend === "DOWN" ? "text-green-700 bg-green-50" : "text-gray-600 bg-gray-100";
  const nearLowest = history && history.min && quote.currentPrice <= history.min * 1.03;
  const unit = type === "FLIGHT" ? "per seat" : "per room / night";

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2 text-xs text-gray-500">
            <span className="relative flex h-2 w-2">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75" />
              <span className="relative inline-flex rounded-full h-2 w-2 bg-green-500" />
            </span>
            Live price {unit}
          </div>
          <div className={`text-3xl font-bold transition-colors ${flash ? "text-red-600" : "text-gray-900"}`}>{inr(quote.currentPrice)}</div>
          {quote.basePrice !== quote.currentPrice && (
            <div className="text-xs text-gray-500">
              Base price <span className="line-through">{inr(quote.basePrice)}</span>
            </div>
          )}
        </div>
        <span className={`inline-flex items-center gap-1 text-sm font-medium px-2 py-1 rounded-full ${trendColor}`}>
          <TrendIcon className="w-4 h-4" />
          {Math.abs(quote.changePercent).toFixed(1)}%
          <span className="text-xs font-normal hidden sm:inline">{quote.trend === "STABLE" ? "steady" : "vs earlier"}</span>
        </span>
      </div>

      {nearLowest && (
        <p className="text-sm text-green-700 bg-green-50 rounded-lg px-3 py-2 flex items-center gap-2">
          <TrendingUp className="w-4 h-4" /> This is close to the lowest price of the last {days} days.
        </p>
      )}

      {/* price freeze */}
      <div className={`rounded-xl border p-3 ${freeze ? "border-blue-300 bg-blue-50" : "bg-white"}`}>
        {freeze ? (
          <div className="space-y-2">
            <div className="flex items-center gap-2 font-medium text-blue-800">
              <Lock className="w-4 h-4" /> Price frozen at {inr(freeze.frozenPrice)} {unit}
            </div>
            <p className="text-xs text-blue-700">
              Valid for {timeUntil(freeze.expiresAt, tick)} (until {fmtDateTime(freeze.expiresAt)}). You pay the lower of the frozen and the
              current price.
            </p>
            <button onClick={release} disabled={busy} className="text-xs text-blue-700 underline inline-flex items-center gap-1">
              <Unlock className="w-3 h-3" /> Release freeze
            </button>
          </div>
        ) : (
          <div className="flex flex-wrap items-center gap-2">
            <div className="flex items-center gap-2 text-sm font-medium mr-auto">
              <Snowflake className="w-4 h-4 text-blue-500" /> Not ready to book?
            </div>
            <select
              aria-label="Freeze duration"
              className="border rounded-md px-2 py-1.5 text-sm bg-white"
              value={hours}
              onChange={(e) => setHours(Number(e.target.value))}
            >
              {[6, 12, 24, 48].map((h) => (
                <option key={h} value={h}>
                  {h} hours
                </option>
              ))}
            </select>
            <button
              onClick={doFreeze}
              disabled={busy}
              className="text-sm px-3 py-1.5 rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
            >
              Freeze this price
            </button>
          </div>
        )}
      </div>

      {/* why the price is what it is */}
      {!compact && (
        <div>
          <h4 className="text-sm font-semibold mb-2">Why this price?</h4>
          {quote.factors.length === 0 ? (
            <p className="text-sm text-gray-500">No extra charges or discounts right now - you see the base price.</p>
          ) : (
            <ul className="space-y-2">
              {quote.factors.map((f: any) => (
                <li key={f.code + f.label} className="text-sm">
                  <div className="flex justify-between gap-2">
                    <span className="font-medium">{f.label}</span>
                    <span className={f.percent > 0 ? "text-red-600" : "text-green-700"}>
                      {f.percent > 0 ? "+" : ""}
                      {f.percent}%
                    </span>
                  </div>
                  <div className="h-1.5 rounded bg-gray-100 mt-1">
                    <div
                      className={`h-1.5 rounded ${factorColor(f.code, f.percent)}`}
                      style={{ width: `${Math.min(100, Math.abs(f.percent) * 2.5)}%` }}
                    />
                  </div>
                  <p className="text-xs text-gray-500 mt-0.5">{f.detail}</p>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {/* history */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <h4 className="text-sm font-semibold">Price history</h4>
          <div className="inline-flex rounded-md border overflow-hidden text-xs">
            {[7, 30, 60].map((d) => (
              <button
                key={d}
                onClick={() => setDays(d)}
                className={`px-2.5 py-1 ${days === d ? "bg-gray-900 text-white" : "bg-white hover:bg-gray-100"}`}
              >
                {d}d
              </button>
            ))}
          </div>
        </div>
        {history && (
          <>
            <PriceChart points={history.points} current={quote.currentPrice} />
            <div className="grid grid-cols-3 gap-2 text-center text-xs mt-1">
              <div className="rounded-lg bg-green-50 py-2">
                <div className="text-gray-500">Lowest</div>
                <div className="font-semibold text-green-700">{inr(history.min)}</div>
              </div>
              <div className="rounded-lg bg-gray-50 py-2">
                <div className="text-gray-500">Average</div>
                <div className="font-semibold">{inr(history.average)}</div>
              </div>
              <div className="rounded-lg bg-red-50 py-2">
                <div className="text-gray-500">Highest</div>
                <div className="font-semibold text-red-700">{inr(history.max)}</div>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
};

export default PriceInsight;
