import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/router";
import { useSelector } from "react-redux";
import { Building2, Compass, Info, MapPin, Plane, Sparkles, Star, ThumbsDown, ThumbsUp, TrendingDown } from "lucide-react";
import { errorMessage, getRecommendations, sendRecommendationFeedback } from "@/api/extra";
import { toast } from "@/lib/toast";
import { fmtDateTime, inr } from "@/lib/format";

const HOTEL_FALLBACK = "https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=800";

/** "Recommended for you": hybrid recommendations with an explanation tooltip and a helpful / not relevant feedback loop. */
const Recommendations = () => {
  const user = useSelector((state: any) => state.user.user);
  const router = useRouter();
  const [data, setData] = useState<any>(null);
  const [type, setType] = useState<"" | "HOTEL" | "FLIGHT" | "DESTINATION">("");
  const [loading, setLoading] = useState(true);
  const [hidden, setHidden] = useState<Set<string>>(new Set());
  const hasData = useRef(false);

  const load = useCallback(
    async (silent = false) => {
      // once something is on screen, refresh quietly instead of flashing a loading state
      if (!silent && !hasData.current) setLoading(true);
      try {
        const d = await getRecommendations(user?.id, 6, type || undefined);
        setData(d);
        hasData.current = true;
        setHidden(new Set());
      } catch (e) {
        // the section simply stays hidden when the backend is not reachable
      } finally {
        setLoading(false);
      }
    },
    [user?.id, type]
  );
  useEffect(() => {
    load();
  }, [load]);

  const feedback = async (item: any, value: "HELPFUL" | "IRRELEVANT") => {
    if (!user?.id) {
      toast({ title: "Log in to give feedback", message: "Your feedback makes the suggestions better.", tone: "warning" });
      return;
    }
    const undo = item.feedback === value;
    try {
      await sendRecommendationFeedback(user.id, item.type, item.itemId, undo ? "NONE" : value);
      setData((d: any) => ({
        ...d,
        items: d.items.map((i: any) => (i.key === item.key ? { ...i, feedback: undo ? null : value } : i)),
      }));
      if (value === "IRRELEVANT" && !undo) {
        setHidden((h) => new Set(h).add(item.key));
        toast({ title: "Thanks - we will show fewer suggestions like this", tone: "success" });
        setTimeout(() => load(true), 600);
      } else if (!undo) {
        toast({ title: "Glad it helped!", message: "We will show more like this.", tone: "success" });
        setTimeout(() => load(true), 600);
      }
    } catch (e) {
      toast({ title: "Could not save feedback", message: errorMessage(e), tone: "error" });
    }
  };

  if (!data && !loading) return null;
  const items = (data?.items || []).filter((i: any) => !hidden.has(i.key));

  return (
    <section className="my-16" aria-labelledby="reco-title">
      <div className="flex flex-wrap items-end justify-between gap-3 mb-6">
        <div>
          <h2 id="reco-title" className="text-2xl font-bold text-white flex items-center gap-2">
            <Sparkles className="w-6 h-6 text-yellow-300" /> {data?.title || "Recommended for you"}
          </h2>
          {data?.profile?.personalised && data.profile.interests.length > 0 && (
            <p className="text-sm text-white/90 mt-1">
              Based on your activity: {data.profile.interests.slice(0, 4).map((i: any) => `${i.label} (${i.percent}%)`).join(", ")}
            </p>
          )}
          {data && !data.profile?.personalised && (
            <p className="text-sm text-white/90 mt-1">
              {user ? "Book, search or view a few things and these suggestions will adapt to you." : "Log in so we can tailor these suggestions to you."}
            </p>
          )}
        </div>
        <div className="inline-flex rounded-lg overflow-hidden border border-white/40 bg-white/10 text-sm text-white" role="tablist">
          {[
            { v: "", l: "All" },
            { v: "HOTEL", l: "Hotels" },
            { v: "FLIGHT", l: "Flights" },
            { v: "DESTINATION", l: "Destinations" },
          ].map((o) => (
            <button
              key={o.v}
              role="tab"
              aria-selected={type === o.v}
              onClick={() => setType(o.v as any)}
              className={`px-4 py-1.5 ${type === o.v ? "bg-white text-gray-900 font-medium" : "hover:bg-white/20"}`}
            >
              {o.l}
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-72 rounded-xl bg-white/70 animate-pulse" />
          ))}
        </div>
      ) : items.length === 0 ? (
        <div className="bg-white rounded-xl p-6 text-gray-600">No suggestions right now - check back soon.</div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {items.map((item: any) => (
            <RecoCard key={item.key} item={item} onOpen={() => router.push(item.href)} onFeedback={(v) => feedback(item, v)} />
          ))}
        </div>
      )}
    </section>
  );
};

const RecoCard = ({ item, onOpen, onFeedback }: { item: any; onOpen: () => void; onFeedback: (v: "HELPFUL" | "IRRELEVANT") => void }) => {
  const [tip, setTip] = useState(false);
  const box = useRef<HTMLDivElement>(null);
  const isHotel = item.type === "HOTEL";
  const isDestination = item.type === "DESTINATION";

  useEffect(() => {
    if (!tip) return;
    const close = (e: MouseEvent) => {
      if (box.current && !box.current.contains(e.target as Node)) setTip(false);
    };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, [tip]);

  return (
    <article className="bg-white rounded-xl shadow-md overflow-hidden flex flex-col">
      <div className="relative">
        {isHotel || isDestination ? (
          <img src={item.image || HOTEL_FALLBACK} alt={item.title} className="w-full h-44 object-cover" loading="lazy" />
        ) : (
          <div className="w-full h-44 bg-gradient-to-br from-sky-500 via-blue-600 to-indigo-700 flex flex-col items-center justify-center text-white">
            <Plane className="w-10 h-10 mb-2" />
            <div className="text-xl font-semibold">{item.subtitle}</div>
            <div className="text-xs text-white/80 mt-1">{item.departureTime ? fmtDateTime(item.departureTime) : ""}</div>
          </div>
        )}
        <span className="absolute top-3 left-3 bg-white/95 text-gray-900 text-xs font-semibold px-2 py-1 rounded-full">{item.matchPercent}% match</span>
        {item.priceChangePercent <= -1 && (
          <span className="absolute top-3 right-3 bg-green-600 text-white text-xs font-semibold px-2 py-1 rounded-full inline-flex items-center gap-1">
            <TrendingDown className="w-3 h-3" /> {Math.abs(Math.round(item.priceChangePercent))}% lower
          </span>
        )}
      </div>

      <div className="p-4 flex flex-col gap-2 flex-1">
        <p className="font-semibold text-blue-700 leading-snug">{item.headline}</p>
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <h3 className="font-semibold text-lg truncate flex items-center gap-1">
              {isHotel ? <Building2 className="w-4 h-4 shrink-0 text-green-600" /> : isDestination ? <Compass className="w-4 h-4 shrink-0 text-orange-500" /> : <Plane className="w-4 h-4 shrink-0 text-blue-600" />}
              {item.title}
            </h3>
            <p className="text-sm text-gray-600 flex items-center gap-1">
              <MapPin className="w-3.5 h-3.5" /> {isHotel ? item.city : item.subtitle}
            </p>
          </div>
          <div className="text-right shrink-0">
            <div className="font-bold">{inr(item.price)}</div>
            <div className="text-[11px] text-gray-500">{isHotel ? "per night" : isDestination ? "stays from, per night" : "per seat"}</div>
          </div>
        </div>
        <div className="flex flex-wrap gap-1.5">
          {item.tags.slice(0, 4).map((t: string) => (
            <span key={t} className="text-[11px] px-2 py-0.5 rounded-full bg-gray-100 text-gray-700 capitalize">
              {t}
            </span>
          ))}
          {item.reviewCount > 0 && (
            <span className="text-[11px] px-2 py-0.5 rounded-full bg-yellow-50 text-yellow-800 inline-flex items-center gap-1">
              <Star className="w-3 h-3 fill-yellow-400 text-yellow-400" /> {item.rating.toFixed(1)} ({item.reviewCount})
            </span>
          )}
        </div>

        <div className="mt-auto pt-2 flex items-center justify-between gap-2">
          <button onClick={onOpen} className="px-4 py-2 rounded-lg bg-blue-600 text-white text-sm hover:bg-blue-700">
            {isHotel ? "View stay" : isDestination ? `Explore ${item.title}` : "View flight"}
          </button>

          {/* why this recommendation? */}
          <div className="relative" ref={box} onMouseEnter={() => setTip(true)} onMouseLeave={() => setTip(false)}>
            <button
              onClick={() => setTip(!tip)}
              onFocus={() => setTip(true)}
              aria-expanded={tip}
              aria-describedby={tip ? `why-${item.key}` : undefined}
              className="inline-flex items-center gap-1 text-xs text-gray-600 hover:text-blue-700 px-2 py-1.5 rounded-md hover:bg-gray-100"
            >
              <Info className="w-4 h-4" /> Why this?
            </button>
            {tip && (
              <div
                id={`why-${item.key}`}
                role="tooltip"
                className="absolute bottom-full right-0 mb-2 w-72 max-w-[80vw] z-20 bg-gray-900 text-white text-xs rounded-lg p-3 shadow-xl"
              >
                <div className="font-semibold text-sm mb-1">Why this recommendation?</div>
                <ul className="space-y-1.5 list-disc pl-4">
                  {item.reasons.map((r: string) => (
                    <li key={r}>{r}</li>
                  ))}
                </ul>
                <div className="mt-2 pt-2 border-t border-white/20 text-white/70">Method: {item.algorithms.join(" + ")}</div>
              </div>
            )}
          </div>
        </div>

        <div className="flex items-center gap-2 pt-2 border-t text-xs text-gray-500">
          <span>Was this useful?</span>
          <button
            onClick={() => onFeedback("HELPFUL")}
            aria-pressed={item.feedback === "HELPFUL"}
            className={`inline-flex items-center gap-1 px-2 py-1 rounded-md border ${item.feedback === "HELPFUL" ? "bg-green-100 border-green-400 text-green-800" : "hover:bg-gray-50"}`}
          >
            <ThumbsUp className="w-3.5 h-3.5" /> Helpful
          </button>
          <button onClick={() => onFeedback("IRRELEVANT")} className="inline-flex items-center gap-1 px-2 py-1 rounded-md border hover:bg-gray-50">
            <ThumbsDown className="w-3.5 h-3.5" /> Not relevant
          </button>
        </div>
      </div>
    </article>
  );
};

export default Recommendations;
