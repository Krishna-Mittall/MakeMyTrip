import { useEffect, useState } from "react";
import { getHotelCalendar } from "@/api/extra";
import { useEvents } from "@/hooks/useEvents";
import { inr } from "@/lib/format";

type Props = { hotelId: string; selected: string; onPick: (date: string) => void };

/** The nightly price of the next 14 days (holidays and weekends are marked) - tap a day to use it as check-in date. */
const PriceCalendar = ({ hotelId, selected, onPick }: Props) => {
  const [days, setDays] = useState<any[]>([]);
  const load = () => getHotelCalendar(hotelId, 14).then((d) => setDays(Array.isArray(d) ? d : [])).catch(() => null);
  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hotelId]);
  useEvents(["PRICE_UPDATE"], (changes) => {
    if (Array.isArray(changes) && changes.some((c: any) => c.type === "HOTEL" && c.id === hotelId)) load();
  });
  if (!days.length) return null;
  const cheapest = Math.min(...days.map((d) => d.price));
  return (
    <div className="mb-4 bg-white rounded-xl border p-3" data-testid="price-calendar">
      <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
        <h3 className="text-sm font-semibold">Price by check-in date (Standard room)</h3>
        <div className="flex items-center gap-3 text-[11px] text-gray-500">
          <span className="inline-flex items-center gap-1"><span className="w-2.5 h-2.5 rounded bg-orange-400" /> Holiday / peak</span>
          <span className="inline-flex items-center gap-1"><span className="w-2.5 h-2.5 rounded bg-amber-300" /> Weekend</span>
          <span className="inline-flex items-center gap-1"><span className="w-2.5 h-2.5 rounded bg-green-400" /> Lowest</span>
        </div>
      </div>
      <div className="flex gap-2 overflow-x-auto pb-1">
        {days.map((d) => {
          const date = new Date(d.date + "T00:00:00");
          const active = selected === d.date;
          const bar = d.tag ? "bg-orange-400" : d.price === cheapest ? "bg-green-400" : d.weekend ? "bg-amber-300" : "bg-gray-200";
          return (
            <button
              key={d.date}
              type="button"
              onClick={() => onPick(d.date)}
              aria-label={`Check-in ${d.date}, ${inr(d.price)}${d.tag ? ", " + d.tag : ""}`}
              title={d.tag ? `${d.tag}: prices +20%` : undefined}
              className={`shrink-0 w-[74px] rounded-lg border text-center overflow-hidden ${active ? "border-blue-500 ring-2 ring-blue-200" : "border-gray-200 hover:border-gray-400"}`}
            >
              <div className={`h-1.5 ${bar}`} />
              <div className="text-[11px] text-gray-500 pt-1">{date.toLocaleDateString("en-IN", { weekday: "short" })}</div>
              <div className="text-sm font-semibold">{date.getDate()} {date.toLocaleDateString("en-IN", { month: "short" })}</div>
              <div className="text-xs font-medium text-gray-800 pb-1.5">{inr(d.price)}</div>
            </button>
          );
        })}
      </div>
      {days.some((d) => d.tag) && (
        <p className="text-xs text-orange-800 mt-1">Holiday and peak dates cost 20% more than normal days.</p>
      )}
    </div>
  );
};

export default PriceCalendar;
