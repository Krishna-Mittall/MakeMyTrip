import { useCallback, useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { BadgeCheck, Camera, Flag, MessageSquare, Pencil, ThumbsUp, Trash2, X } from "lucide-react";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import {
  deleteReview,
  errorMessage,
  flagReview,
  getFlagReasons,
  getReviews,
  photoSrc,
  replyToReview,
  saveReview,
  toggleHelpful,
  uploadReviewPhoto,
} from "@/api/extra";
import { shrinkImage } from "@/lib/image";
import { toast } from "@/lib/toast";
import { ago } from "@/lib/format";
import { StarInput, StarRow } from "./Stars";

type Props = { targetType: "HOTEL" | "FLIGHT"; targetId: string; targetName?: string; onChanged?: () => void };

const SORTS = [
  { value: "helpful", label: "Most helpful" },
  { value: "newest", label: "Newest" },
  { value: "highest", label: "Highest rated" },
  { value: "lowest", label: "Lowest rated" },
];

/** Ratings and reviews: write, photos, helpful votes, replies, flagging, sorting and filtering. */
const ReviewSection = ({ targetType, targetId, targetName, onChanged }: Props) => {
  const user = useSelector((state: any) => state.user.user);
  const [data, setData] = useState<any>(null);
  const [sort, setSort] = useState("helpful");
  const [rating, setRating] = useState<number | null>(null);
  const [withPhotos, setWithPhotos] = useState(false);
  const [verifiedOnly, setVerifiedOnly] = useState(false);
  const [writing, setWriting] = useState(false);
  const [flagging, setFlagging] = useState<any>(null);
  const [lightbox, setLightbox] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const d = await getReviews({
        targetType,
        targetId,
        sort,
        rating: rating ?? undefined,
        withPhotos,
        verifiedOnly,
        userId: user?.id,
      });
      setData(d);
    } catch (e) {
      toast({ title: "Could not load reviews", message: errorMessage(e), tone: "error" });
    } finally {
      setLoading(false);
    }
  }, [targetType, targetId, sort, rating, withPhotos, verifiedOnly, user?.id]);

  useEffect(() => {
    load();
  }, [load]);

  const mine = data?.reviews?.find((r: any) => r.mine);
  const summary = data?.summary;
  const changed = () => {
    load();
    onChanged?.();
  };

  const vote = async (r: any) => {
    if (!user) return toast({ title: "Log in to mark reviews as helpful", tone: "warning" });
    try {
      await toggleHelpful(r.id, user.id);
      load();
    } catch (e) {
      toast({ title: errorMessage(e), tone: "warning" });
    }
  };
  const remove = async (r: any) => {
    if (!confirm("Delete your review?")) return;
    try {
      await deleteReview(r.id, user.id);
      changed();
    } catch (e) {
      toast({ title: errorMessage(e), tone: "error" });
    }
  };

  return (
    <section className="bg-white rounded-xl shadow-sm p-4 sm:p-6" id="reviews">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <h2 className="text-xl font-bold">Ratings & reviews</h2>
        {user ? (
          <button
            onClick={() => setWriting(true)}
            className="inline-flex items-center gap-2 text-sm px-4 py-2 rounded-lg bg-blue-600 text-white hover:bg-blue-700"
          >
            <Pencil className="w-4 h-4" /> {mine ? "Edit your review" : "Write a review"}
          </button>
        ) : (
          <span className="text-sm text-gray-500">Log in to write a review</span>
        )}
      </div>

      {summary && (
        <div className="grid sm:grid-cols-[180px_1fr] gap-4 sm:gap-8 mb-5">
          <div className="text-center sm:text-left">
            <div className="text-5xl font-bold">{summary.count ? summary.average.toFixed(1) : "–"}</div>
            <StarRow value={summary.average || 0} size={20} />
            <div className="text-sm text-gray-500 mt-1">
              {summary.count} review{summary.count === 1 ? "" : "s"}
            </div>
          </div>
          <div className="space-y-1.5">
            {[5, 4, 3, 2, 1].map((n) => {
              const c = summary.distribution?.[String(n)] || 0;
              const pct = summary.count ? (c / summary.count) * 100 : 0;
              return (
                <button
                  key={n}
                  onClick={() => setRating(rating === n ? null : n)}
                  className={`w-full flex items-center gap-2 text-sm rounded px-1 ${rating === n ? "bg-blue-50" : "hover:bg-gray-50"}`}
                  aria-label={`Show ${n} star reviews`}
                >
                  <span className="w-8 text-left">{n} ★</span>
                  <span className="flex-1 h-2 bg-gray-100 rounded">
                    <span className="block h-2 bg-yellow-400 rounded" style={{ width: `${pct}%` }} />
                  </span>
                  <span className="w-8 text-right text-gray-500">{c}</span>
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* sort and filter */}
      <div className="flex flex-wrap items-center gap-2 mb-4">
        <label className="text-sm text-gray-600 flex items-center gap-2">
          Sort by
          <select className="border rounded-md px-2 py-1.5 text-sm bg-white" value={sort} onChange={(e) => setSort(e.target.value)}>
            {SORTS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>
        </label>
        <button
          onClick={() => setWithPhotos(!withPhotos)}
          className={`text-sm px-3 py-1.5 rounded-full border inline-flex items-center gap-1 ${withPhotos ? "bg-blue-600 text-white border-blue-600" : "bg-white"}`}
        >
          <Camera className="w-3.5 h-3.5" /> With photos
        </button>
        <button
          onClick={() => setVerifiedOnly(!verifiedOnly)}
          className={`text-sm px-3 py-1.5 rounded-full border inline-flex items-center gap-1 ${verifiedOnly ? "bg-blue-600 text-white border-blue-600" : "bg-white"}`}
        >
          <BadgeCheck className="w-3.5 h-3.5" /> Verified {targetType === "HOTEL" ? "stays" : "flyers"}
        </button>
        {(rating || withPhotos || verifiedOnly) && (
          <button
            className="text-sm text-gray-500 underline"
            onClick={() => {
              setRating(null);
              setWithPhotos(false);
              setVerifiedOnly(false);
            }}
          >
            Clear filters
          </button>
        )}
      </div>

      {loading && <div className="h-24 bg-gray-100 rounded-lg animate-pulse" />}
      {!loading && data?.reviews?.length === 0 && (
        <p className="text-sm text-gray-500 py-6 text-center">
          {summary?.count ? "No reviews match these filters." : "No reviews yet. Be the first to share your experience!"}
        </p>
      )}
      <div className="divide-y">
        {data?.reviews?.map((r: any) => (
          <ReviewCard
            key={r.id}
            r={r}
            user={user}
            onVote={() => vote(r)}
            onFlag={() => (user ? setFlagging(r) : toast({ title: "Log in to report a review", tone: "warning" }))}
            onDelete={() => remove(r)}
            onPhoto={setLightbox}
            onReplied={load}
          />
        ))}
      </div>

      {writing && (
        <ReviewForm
          open
          existing={mine}
          targetType={targetType}
          targetId={targetId}
          targetName={targetName}
          onClose={() => setWriting(false)}
          onSaved={() => {
            setWriting(false);
            changed();
          }}
        />
      )}
      {flagging && <FlagDialog review={flagging} onClose={() => setFlagging(null)} userId={user?.id} onDone={load} />}
      <Dialog open={!!lightbox} onOpenChange={(o) => !o && setLightbox(null)}>
        <DialogContent className="sm:max-w-[820px] bg-white p-2">
          <DialogHeader className="sr-only">
            <DialogTitle>Review photo</DialogTitle>
          </DialogHeader>
          {lightbox && <img src={photoSrc(lightbox)} alt="Review photo" className="w-full max-h-[80vh] object-contain" />}
        </DialogContent>
      </Dialog>
    </section>
  );
};

const ReviewCard = ({ r, user, onVote, onFlag, onDelete, onPhoto, onReplied }: any) => {
  const [replying, setReplying] = useState(false);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);

  const send = async () => {
    if (!user) return toast({ title: "Log in to reply", tone: "warning" });
    setBusy(true);
    try {
      await replyToReview(r.id, user.id, text);
      setText("");
      setReplying(false);
      onReplied();
    } catch (e) {
      toast({ title: errorMessage(e), tone: "error" });
    } finally {
      setBusy(false);
    }
  };

  return (
    <article className="py-4">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-full bg-gradient-to-br from-blue-500 to-red-500 text-white flex items-center justify-center font-semibold">
            {(r.userName || "?").charAt(0).toUpperCase()}
          </div>
          <div>
            <div className="font-medium flex items-center gap-2 flex-wrap">
              {r.userName}
              {r.verified && (
                <span className="text-[11px] text-green-700 bg-green-50 px-1.5 py-0.5 rounded-full inline-flex items-center gap-1">
                  <BadgeCheck className="w-3 h-3" /> Verified booking
                </span>
              )}
            </div>
            <div className="text-xs text-gray-500">{ago(r.createdAt)}{r.updatedAt && r.updatedAt !== r.createdAt ? " · edited" : ""}</div>
          </div>
        </div>
        <StarRow value={r.rating} />
      </div>
      {r.title && <h4 className="font-semibold mt-2">{r.title}</h4>}
      <p className="text-gray-700 mt-1 whitespace-pre-line break-words">{r.text}</p>
      {r.photos?.length > 0 && (
        <div className="flex flex-wrap gap-2 mt-3">
          {r.photos.map((p: string) => (
            <button key={p} onClick={() => onPhoto(p)} className="rounded-lg overflow-hidden border">
              <img src={photoSrc(p)} alt="Photo from the review" className="h-20 w-20 sm:h-24 sm:w-24 object-cover" loading="lazy" />
            </button>
          ))}
        </div>
      )}
      <div className="flex flex-wrap items-center gap-4 mt-3 text-sm text-gray-600">
        <button
          onClick={onVote}
          disabled={r.mine}
          className={`inline-flex items-center gap-1 hover:text-blue-600 disabled:opacity-60 ${r.helpfulByMe ? "text-blue-600 font-medium" : ""}`}
        >
          <ThumbsUp className={`w-4 h-4 ${r.helpfulByMe ? "fill-blue-100" : ""}`} /> Helpful ({r.helpfulCount})
        </button>
        <button onClick={() => setReplying(!replying)} className="inline-flex items-center gap-1 hover:text-blue-600">
          <MessageSquare className="w-4 h-4" /> Reply{r.replies?.length ? ` (${r.replies.length})` : ""}
        </button>
        {!r.mine && (
          <button onClick={onFlag} disabled={r.flaggedByMe} className="inline-flex items-center gap-1 hover:text-red-600 disabled:opacity-60">
            <Flag className="w-4 h-4" /> {r.flaggedByMe ? "Reported" : "Report"}
          </button>
        )}
        {r.mine && (
          <button onClick={onDelete} className="inline-flex items-center gap-1 hover:text-red-600">
            <Trash2 className="w-4 h-4" /> Delete
          </button>
        )}
      </div>
      {(r.replies?.length > 0 || replying) && (
        <div className="mt-3 ml-4 sm:ml-12 space-y-2 border-l-2 pl-3">
          {r.replies.map((rep: any) => (
            <div key={rep.id} className="text-sm">
              <span className="font-medium">{rep.userName}</span> <span className="text-gray-400 text-xs">{ago(rep.createdAt)}</span>
              <p className="text-gray-700 break-words">{rep.text}</p>
            </div>
          ))}
          {replying && (
            <div className="flex gap-2">
              <input
                value={text}
                onChange={(e) => setText(e.target.value)}
                maxLength={500}
                placeholder={user ? "Write a reply..." : "Log in to reply"}
                className="flex-1 border rounded-md px-3 py-1.5 text-sm"
                onKeyDown={(e) => e.key === "Enter" && text.trim().length > 1 && send()}
              />
              <button
                onClick={send}
                disabled={busy || text.trim().length < 2}
                className="px-3 py-1.5 text-sm rounded-md bg-blue-600 text-white disabled:opacity-50"
              >
                Post
              </button>
            </div>
          )}
        </div>
      )}
    </article>
  );
};

const FlagDialog = ({ review, onClose, userId, onDone }: any) => {
  const [reasons, setReasons] = useState<string[]>([]);
  const [reason, setReason] = useState("");
  const [details, setDetails] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    getFlagReasons().then((l) => {
      setReasons(l);
      setReason(l[0]);
    });
  }, []);
  const submit = async () => {
    setBusy(true);
    try {
      const res = await flagReview(review.id, userId, reason, details);
      toast({ title: "Thanks for reporting", message: res.message, tone: "success" });
      onDone();
      onClose();
    } catch (e) {
      toast({ title: errorMessage(e), tone: "error" });
    } finally {
      setBusy(false);
    }
  };
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-[440px] bg-white">
        <DialogHeader>
          <DialogTitle>Report this review</DialogTitle>
        </DialogHeader>
        <p className="text-sm text-gray-600">Tell us what is wrong. A moderator will check it and remove it if it breaks our guidelines.</p>
        <select className="border rounded-md px-3 py-2 w-full bg-white" value={reason} onChange={(e) => setReason(e.target.value)}>
          {reasons.map((r) => (
            <option key={r}>{r}</option>
          ))}
        </select>
        <textarea
          className="border rounded-md px-3 py-2 w-full text-sm"
          rows={3}
          maxLength={300}
          placeholder="More details (optional)"
          value={details}
          onChange={(e) => setDetails(e.target.value)}
        />
        <button onClick={submit} disabled={busy || !reason} className="w-full py-2 rounded-md bg-red-600 text-white disabled:opacity-50">
          Submit report
        </button>
      </DialogContent>
    </Dialog>
  );
};

const ReviewForm = ({ open, existing, targetType, targetId, targetName, onClose, onSaved }: any) => {
  const user = useSelector((state: any) => state.user.user);
  const [rating, setRating] = useState<number>(existing?.rating || 0);
  const [title, setTitle] = useState(existing?.title || "");
  const [text, setText] = useState(existing?.text || "");
  const [photos, setPhotos] = useState<{ id: string; url: string }[]>(
    (existing?.photos || []).map((p: string) => ({ id: p.split("/").pop(), url: p }))
  );
  const [busy, setBusy] = useState(false);
  const [uploading, setUploading] = useState(false);

  const addPhotos = async (files: FileList | null) => {
    if (!files) return;
    const list = Array.from(files).slice(0, 4 - photos.length);
    setUploading(true);
    try {
      for (const file of list) {
        const blob = await shrinkImage(file);
        const res = await uploadReviewPhoto(user.id, blob);
        setPhotos((p) => [...p, { id: res.id, url: res.url }]);
      }
    } catch (e) {
      toast({ title: "Photo not added", message: errorMessage(e), tone: "error" });
    } finally {
      setUploading(false);
    }
  };

  const submit = async () => {
    if (rating < 1) return toast({ title: "Please choose a star rating", tone: "warning" });
    if (text.trim().length < 10) return toast({ title: "Please write at least 10 characters", tone: "warning" });
    setBusy(true);
    try {
      await saveReview({ userId: user.id, targetType, targetId, rating, title, text, photoIds: photos.map((p) => p.id) });
      toast({ title: existing ? "Review updated" : "Thanks for your review!", tone: "success" });
      onSaved();
    } catch (e) {
      toast({ title: "Could not save the review", message: errorMessage(e), tone: "error" });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-[520px] bg-white max-h-[92vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{existing ? "Edit your review" : "Write a review"}{targetName ? ` · ${targetName}` : ""}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div>
            <div className="text-sm font-medium mb-1">Your rating</div>
            <StarInput value={rating} onChange={setRating} />
          </div>
          <input
            className="border rounded-md px-3 py-2 w-full"
            placeholder="Headline (optional)"
            maxLength={100}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
          <textarea
            className="border rounded-md px-3 py-2 w-full"
            rows={5}
            maxLength={2000}
            placeholder="What did you like or dislike? Share details that help other travellers."
            value={text}
            onChange={(e) => setText(e.target.value)}
          />
          <div>
            <div className="text-sm font-medium mb-2">Photos (up to 4)</div>
            <div className="flex flex-wrap gap-2">
              {photos.map((p) => (
                <div key={p.id} className="relative">
                  <img src={photoSrc(p.url)} alt="Your upload" className="h-20 w-20 object-cover rounded-lg border" />
                  <button
                    type="button"
                    aria-label="Remove photo"
                    onClick={() => setPhotos(photos.filter((x) => x.id !== p.id))}
                    className="absolute -top-2 -right-2 bg-white border rounded-full p-0.5 shadow"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                </div>
              ))}
              {photos.length < 4 && (
                <label className="h-20 w-20 border-2 border-dashed rounded-lg flex flex-col items-center justify-center text-xs text-gray-500 cursor-pointer hover:bg-gray-50">
                  <Camera className="w-5 h-5" />
                  {uploading ? "Adding..." : "Add"}
                  <input type="file" accept="image/png,image/jpeg,image/webp" multiple hidden onChange={(e) => addPhotos(e.target.files)} />
                </label>
              )}
            </div>
          </div>
          <button onClick={submit} disabled={busy || uploading} className="w-full py-2.5 rounded-lg bg-blue-600 text-white font-medium disabled:opacity-50">
            {busy ? "Saving..." : existing ? "Update review" : "Submit review"}
          </button>
        </div>
      </DialogContent>
    </Dialog>
  );
};

export default ReviewSection;
