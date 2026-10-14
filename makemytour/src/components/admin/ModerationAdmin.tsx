import { useCallback, useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { Check, Trash2 } from "lucide-react";
import { errorMessage, modDismiss, modQueue, modRemove, modStats, photoSrc } from "@/api/extra";
import { StarRow } from "@/components/reviews/Stars";
import { toast } from "@/lib/toast";
import { ago } from "@/lib/format";

/** Moderators (and admins) check the reviews that users flagged and keep or remove them. */
const ModerationAdmin = () => {
  const user = useSelector((state: any) => state.user.user);
  const [status, setStatus] = useState("PENDING");
  const [items, setItems] = useState<any[]>([]);
  const [stats, setStats] = useState<any>(null);
  const [error, setError] = useState("");
  const [notes, setNotes] = useState<Record<string, string>>({});

  const load = useCallback(async () => {
    if (!user?.id) return;
    try {
      const [q, s] = await Promise.all([modQueue(user.id, status), modStats(user.id)]);
      setItems(q);
      setStats(s);
      setError("");
    } catch (e) {
      setError(errorMessage(e));
    }
  }, [user?.id, status]);
  useEffect(() => {
    load();
  }, [load]);

  const act = async (id: string, action: "dismiss" | "remove") => {
    try {
      if (action === "remove") await modRemove(id, user.id, notes[id]);
      else await modDismiss(id, user.id, notes[id]);
      toast({ title: action === "remove" ? "Review removed" : "Review kept", tone: "success" });
      load();
    } catch (e) {
      toast({ title: errorMessage(e), tone: "error" });
    }
  };

  if (!user) return <p className="text-gray-600">Please log in as a moderator.</p>;
  if (error) return <p className="text-red-600">{error}</p>;

  return (
    <div className="space-y-4">
      {stats && (
        <div className="grid grid-cols-3 gap-3">
          {[
            { k: "PENDING", l: "Waiting for a decision", v: stats.pending },
            { k: "KEPT", l: "Kept", v: stats.kept },
            { k: "REMOVED", l: "Removed", v: stats.removed },
          ].map((s) => (
            <button
              key={s.k}
              onClick={() => setStatus(s.k)}
              className={`rounded-xl border p-3 text-left ${status === s.k ? "border-blue-500 bg-blue-50" : "bg-white hover:bg-gray-50"}`}
            >
              <div className="text-xs text-gray-500">{s.l}</div>
              <div className="text-2xl font-bold">{s.v}</div>
            </button>
          ))}
        </div>
      )}
      {items.length === 0 && <p className="text-center text-gray-500 py-8">Nothing here. Flagged reviews appear in this list.</p>}
      {items.map((item) => {
        const r = item.review;
        return (
          <div key={r.id} className="border rounded-xl p-4 space-y-3">
            <div className="flex flex-wrap justify-between gap-2">
              <div>
                <div className="font-medium">
                  {r.userName} <span className="text-gray-500 font-normal">on {r.targetName}</span>
                </div>
                <div className="text-xs text-gray-500">
                  {r.targetType} · {ago(r.createdAt)} · {r.flagCount} flag{r.flagCount === 1 ? "" : "s"} · status {r.status}
                </div>
              </div>
              <StarRow value={r.rating} />
            </div>
            {r.title && <div className="font-semibold">{r.title}</div>}
            <p className="text-gray-700 whitespace-pre-line break-words">{r.text}</p>
            {r.photos?.length > 0 && (
              <div className="flex gap-2 flex-wrap">
                {r.photos.map((p: string) => (
                  <img key={p} src={photoSrc(p)} alt="Review" className="h-16 w-16 object-cover rounded" />
                ))}
              </div>
            )}
            <div className="bg-red-50 rounded-lg p-3 text-sm">
              <div className="font-medium text-red-800 mb-1">Reports</div>
              <ul className="space-y-0.5 text-red-900">
                {item.flags.map((f: any, i: number) => (
                  <li key={i}>
                    <b>{f.reason}</b>
                    {f.details ? ` - ${f.details}` : ""}
                  </li>
                ))}
              </ul>
            </div>
            {status === "PENDING" ? (
              <div className="flex flex-wrap items-center gap-2">
                <input
                  className="flex-1 min-w-[160px] border rounded-md px-3 py-1.5 text-sm"
                  placeholder="Note (optional)"
                  value={notes[r.id] || ""}
                  onChange={(e) => setNotes({ ...notes, [r.id]: e.target.value })}
                />
                <button onClick={() => act(r.id, "dismiss")} className="inline-flex items-center gap-1 px-3 py-1.5 rounded-md border hover:bg-gray-50 text-sm">
                  <Check className="w-4 h-4" /> Keep review
                </button>
                <button onClick={() => act(r.id, "remove")} className="inline-flex items-center gap-1 px-3 py-1.5 rounded-md bg-red-600 text-white text-sm hover:bg-red-700">
                  <Trash2 className="w-4 h-4" /> Remove review
                </button>
              </div>
            ) : (
              <p className="text-xs text-gray-500">
                Decided by {item.moderatedBy || "-"} {item.moderationNote ? `· "${item.moderationNote}"` : ""}
              </p>
            )}
          </div>
        );
      })}
    </div>
  );
};

export default ModerationAdmin;
