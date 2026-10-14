import { useMemo, useRef, useState } from "react";
import { fmtDateTime, inr } from "@/lib/format";

type Point = { t: string; price: number; reason?: string };

/** A small line chart (SVG) of the price history with a hover tooltip. */
const PriceChart = ({ points, current }: { points: Point[]; current?: number }) => {
  const W = 600;
  const H = 180;
  const pad = { l: 48, r: 12, t: 12, b: 24 };
  const [hover, setHover] = useState<number | null>(null);
  const svg = useRef<SVGSVGElement>(null);

  const data = useMemo(() => {
    if (!points.length) return null;
    const times = points.map((p) => new Date(p.t).getTime());
    const prices = points.map((p) => p.price);
    const minP = Math.min(...prices);
    const maxP = Math.max(...prices);
    const span = Math.max(maxP - minP, maxP * 0.02, 1);
    const lo = minP - span * 0.15;
    const hi = maxP + span * 0.15;
    const t0 = times[0];
    const t1 = Math.max(times[times.length - 1], t0 + 1);
    const x = (t: number) => pad.l + ((t - t0) / (t1 - t0)) * (W - pad.l - pad.r);
    const y = (p: number) => pad.t + (1 - (p - lo) / (hi - lo)) * (H - pad.t - pad.b);
    const coords = points.map((p, i) => ({ x: x(times[i]), y: y(p.price) }));
    const ticks = [lo + (hi - lo) * 0.1, (lo + hi) / 2, hi - (hi - lo) * 0.1];
    return { coords, ticks, y, minIdx: prices.indexOf(minP), t0, t1 };
  }, [points]);

  if (!data) return <div className="h-44 flex items-center justify-center text-sm text-gray-500">No price history yet.</div>;

  const line = data.coords.map((c, i) => `${i === 0 ? "M" : "L"}${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(" ");
  const area = `${line} L${data.coords[data.coords.length - 1].x},${H - pad.b} L${data.coords[0].x},${H - pad.b} Z`;

  const move = (clientX: number) => {
    const rect = svg.current?.getBoundingClientRect();
    if (!rect) return;
    const x = ((clientX - rect.left) / rect.width) * W;
    let best = 0;
    data.coords.forEach((c, i) => {
      if (Math.abs(c.x - x) < Math.abs(data.coords[best].x - x)) best = i;
    });
    setHover(best);
  };

  const shown = hover !== null ? points[hover] : null;
  const dateLabel = (t: number) => new Date(t).toLocaleDateString("en-IN", { day: "numeric", month: "short" });

  return (
    <div className="relative">
      <svg
        ref={svg}
        viewBox={`0 0 ${W} ${H}`}
        className="w-full h-auto touch-none select-none"
        role="img"
        aria-label="Price history chart"
        onMouseMove={(e) => move(e.clientX)}
        onTouchMove={(e) => move(e.touches[0].clientX)}
        onMouseLeave={() => setHover(null)}
        onTouchEnd={() => setHover(null)}
      >
        <defs>
          <linearGradient id="priceFill" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor="#ef4444" stopOpacity="0.25" />
            <stop offset="100%" stopColor="#ef4444" stopOpacity="0" />
          </linearGradient>
        </defs>
        {data.ticks.map((t, i) => (
          <g key={i}>
            <line x1={pad.l} x2={W - pad.r} y1={data.y(t)} y2={data.y(t)} stroke="#e5e7eb" strokeDasharray="3 3" />
            <text x={pad.l - 6} y={data.y(t) + 4} textAnchor="end" fontSize="11" fill="#6b7280">
              {Math.round(t).toLocaleString("en-IN")}
            </text>
          </g>
        ))}
        <text x={pad.l} y={H - 6} fontSize="11" fill="#6b7280">
          {dateLabel(data.t0)}
        </text>
        <text x={W - pad.r} y={H - 6} fontSize="11" fill="#6b7280" textAnchor="end">
          {dateLabel(data.t1)}
        </text>
        <path d={area} fill="url(#priceFill)" />
        <path d={line} fill="none" stroke="#ef4444" strokeWidth="2" strokeLinejoin="round" />
        <circle cx={data.coords[data.minIdx].x} cy={data.coords[data.minIdx].y} r="4" fill="#16a34a" stroke="white" strokeWidth="1.5">
          <title>Lowest price</title>
        </circle>
        <circle
          cx={data.coords[data.coords.length - 1].x}
          cy={data.coords[data.coords.length - 1].y}
          r="4.5"
          fill="#ef4444"
          stroke="white"
          strokeWidth="1.5"
        />
        {hover !== null && (
          <g>
            <line x1={data.coords[hover].x} x2={data.coords[hover].x} y1={pad.t} y2={H - pad.b} stroke="#9ca3af" />
            <circle cx={data.coords[hover].x} cy={data.coords[hover].y} r="5" fill="#ef4444" stroke="white" strokeWidth="2" />
          </g>
        )}
      </svg>
      <div className="h-9 text-xs mt-1 text-gray-600">
        {shown ? (
          <span>
            <b>{inr(shown.price)}</b> on {fmtDateTime(shown.t)}
            {shown.reason ? <span className="text-gray-400"> · {shown.reason}</span> : null}
          </span>
        ) : (
          <span className="text-gray-400">
            Move over the chart to see the price on a day
            {current ? ` · now ${inr(current)}` : ""}
          </span>
        )}
      </div>
    </div>
  );
};

export default PriceChart;
