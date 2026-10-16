import { useEffect, useState } from "react";
import { CalendarClock } from "lucide-react";
import { getSurges } from "@/api/extra";

const short = (iso: string) => new Date(iso + "T00:00:00").toLocaleDateString("en-IN", { day: "numeric", month: "short" });

/** Upcoming holidays and peak periods that raise the prices, so that travellers can plan (and freeze a price) in time. */
const PriceAlerts = () => {
  const [surges, setSurges] = useState<any[]>([]);
  useEffect(() => {
    getSurges().then((list) => setSurges(Array.isArray(list) ? list.slice(0, 3) : [])).catch(() => null);
  }, []);
  if (!surges.length) return null;
  return (
    <div className="mt-6 rounded-xl border border-orange-200 bg-orange-50 p-4" data-testid="price-alerts">
      <div className="flex items-center gap-2 font-semibold text-orange-900 mb-2">
        <CalendarClock className="w-5 h-5" /> Prices go up around these dates
      </div>
      <ul className="grid sm:grid-cols-3 gap-2 text-sm">
        {surges.map((s) => (
          <li key={s.name + s.from} className="rounded-lg bg-white border border-orange-100 px-3 py-2">
            <div className="font-medium text-gray-900">{s.name}</div>
            <div className="text-gray-600">
              {short(s.from)} - {short(s.to)} · <span className="text-red-600 font-medium">+{s.percent}%</span>
            </div>
          </li>
        ))}
      </ul>
      <p className="text-xs text-orange-900/80 mt-2">Book early, or use "Freeze this price" on a flight or hotel to hold today&apos;s price for up to 48 hours.</p>
    </div>
  );
};

export default PriceAlerts;
