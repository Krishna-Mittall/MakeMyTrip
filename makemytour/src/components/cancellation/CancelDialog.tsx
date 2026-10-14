import { useEffect, useMemo, useState } from "react";
import { useSelector } from "react-redux";
import { AlertTriangle, CheckCircle2, Info } from "lucide-react";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { cancelBooking, errorMessage, getCancelReasons, getRefundPolicy, previewCancel } from "@/api/extra";
import { fmtDateTime, inr } from "@/lib/format";

type Props = { booking: any; open: boolean; onClose: () => void; onCancelled: (refund: any) => void };

/** Cancel a whole booking or only some seats / rooms, with a live refund preview from the cancellation policy. */
const CancelDialog = ({ booking, open, onClose, onCancelled }: Props) => {
  const user = useSelector((state: any) => state.user.user);
  const isFlight = booking.type === "Flight";
  const remaining = booking.quantity - (booking.cancelledQuantity || 0);
  const [reasons, setReasons] = useState<string[]>([]);
  const [policy, setPolicy] = useState<any>(null);
  const [reason, setReason] = useState("");
  const [comments, setComments] = useState("");
  const [seats, setSeats] = useState<string[]>([]);
  const [qty, setQty] = useState(remaining);
  const [preview, setPreview] = useState<any>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState<any>(null);

  useEffect(() => {
    getCancelReasons().then((r) => {
      setReasons(r);
      setReason(r[0]);
    });
    getRefundPolicy().then(setPolicy);
  }, []);

  // all seats are selected at first (cancel everything)
  useEffect(() => {
    if (isFlight && booking.seats?.length) setSeats([...booking.seats]);
  }, [isFlight, booking.seats]);

  const selectedCount = isFlight && booking.seats?.length ? seats.length : qty;

  useEffect(() => {
    if (!user?.id || selectedCount < 1) {
      setPreview(null);
      return;
    }
    let cancelled = false;
    previewCancel(user.id, booking.reference, isFlight && booking.seats?.length ? seats : undefined, isFlight && booking.seats?.length ? undefined : qty)
      .then((p) => {
        if (!cancelled) {
          setPreview(p);
          setError("");
        }
      })
      .catch((e) => !cancelled && setError(errorMessage(e)));
    return () => {
      cancelled = true;
    };
  }, [user?.id, booking.reference, seats, qty, isFlight, booking.seats, selectedCount]);

  const table = useMemo(() => (policy ? (isFlight ? policy.flight : policy.hotel) : []), [policy, isFlight]);

  const confirm = async () => {
    setBusy(true);
    setError("");
    try {
      const refund = await cancelBooking(
        user.id,
        booking.reference,
        reason,
        isFlight && booking.seats?.length ? seats : undefined,
        isFlight && booking.seats?.length ? undefined : qty,
        comments
      );
      setDone(refund);
      onCancelled(refund);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-[560px] bg-white max-h-[92vh] overflow-y-auto">
        {done ? (
          <div className="text-center space-y-3 py-2">
            <CheckCircle2 className="w-14 h-14 text-green-600 mx-auto" />
            <h3 className="text-xl font-bold">Cancellation confirmed</h3>
            <p className="text-gray-600">
              Refund of <b>{inr(done.refundAmount)}</b> ({done.refundPercent}%) is <b>{done.status.toLowerCase()}</b>.
            </p>
            <div className="rounded-lg bg-gray-50 border p-3 text-sm text-left space-y-1">
              <div>
                Refund reference: <b>{done.refundReference}</b>
              </div>
              <div>Expected to be processed by {fmtDateTime(done.expectedProcessedBy)}</div>
              <div>Expected in your account by {fmtDateTime(done.expectedCompletedBy)}</div>
              {done.timelineNote && <p className="text-xs text-gray-500 pt-1">{done.timelineNote}</p>}
            </div>
            <button onClick={onClose} className="w-full py-2 rounded-lg bg-gray-900 text-white">
              Track the refund
            </button>
          </div>
        ) : (
          <>
            <DialogHeader>
              <DialogTitle>
                Cancel {booking.title ? booking.title : booking.type} <span className="text-gray-400 font-normal text-sm">· {booking.reference}</span>
              </DialogTitle>
            </DialogHeader>

            {/* what to cancel */}
            {isFlight && booking.seats?.length ? (
              <div>
                <div className="text-sm font-medium mb-2">Which seats do you want to cancel?</div>
                <div className="flex flex-wrap gap-2">
                  {booking.seats.map((s: string) => {
                    const on = seats.includes(s);
                    return (
                      <button
                        key={s}
                        type="button"
                        onClick={() => setSeats(on ? seats.filter((x) => x !== s) : [...seats, s])}
                        className={`px-3 py-1.5 rounded-lg border text-sm ${on ? "bg-red-50 border-red-400 text-red-700" : "bg-white text-gray-600"}`}
                        aria-pressed={on}
                      >
                        Seat {s}
                      </button>
                    );
                  })}
                </div>
                <p className="text-xs text-gray-500 mt-1">
                  {seats.length === booking.seats.length ? "Whole booking will be cancelled." : "Partial cancellation: the other seats stay booked."}
                </p>
              </div>
            ) : (
              <div>
                <div className="text-sm font-medium mb-2">How many {isFlight ? "tickets" : "rooms"} do you want to cancel?</div>
                <div className="inline-flex items-center border rounded-lg">
                  <button type="button" className="px-3 py-1.5" onClick={() => setQty(Math.max(1, qty - 1))} aria-label="Fewer">
                    −
                  </button>
                  <span className="px-4 font-semibold" aria-live="polite">{qty}</span>
                  <button type="button" className="px-3 py-1.5" onClick={() => setQty(Math.min(remaining, qty + 1))} aria-label="More">
                    +
                  </button>
                </div>
                <span className="text-xs text-gray-500 ml-2">of {remaining} booked</span>
              </div>
            )}

            {/* reason */}
            <label className="block text-sm font-medium">
              Reason for cancellation
              <select className="mt-1 block w-full border rounded-lg px-3 py-2 bg-white font-normal" value={reason} onChange={(e) => setReason(e.target.value)}>
                {reasons.map((r) => (
                  <option key={r}>{r}</option>
                ))}
              </select>
            </label>
            <textarea
              className="border rounded-lg px-3 py-2 w-full text-sm"
              rows={2}
              maxLength={300}
              placeholder="Anything else you want to tell us? (optional)"
              value={comments}
              onChange={(e) => setComments(e.target.value)}
            />

            {/* refund preview */}
            {preview && (
              <div className="rounded-xl border bg-gray-50 p-4 space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-sm text-gray-600">You paid for {preview.quantity} {isFlight ? "seat(s)" : "room(s)"}</span>
                  <span className="font-medium">{inr(preview.originalAmount)}</span>
                </div>
                <div className="flex items-center justify-between text-red-600 text-sm">
                  <span>Cancellation charge ({100 - preview.refundPercent}%)</span>
                  <span>− {inr(preview.deduction)}</span>
                </div>
                <div className="flex items-center justify-between text-lg font-bold text-green-700 border-t pt-2">
                  <span>Your refund ({preview.refundPercent}%)</span>
                  <span>{inr(preview.refundAmount)}</span>
                </div>
                <p className="text-xs text-gray-600 flex gap-1">
                  <Info className="w-3.5 h-3.5 mt-0.5 shrink-0" /> {preview.policyApplied}. {isFlight ? "Departure" : "Check-in"} in about {Math.max(0, Math.round(preview.hoursToTravel))} hours.
                </p>
              </div>
            )}

            {/* the policy */}
            {table.length > 0 && (
              <details className="text-sm">
                <summary className="cursor-pointer text-blue-600">View the refund policy</summary>
                <table className="w-full mt-2 text-xs border rounded">
                  <tbody>
                    {table.map((row: any) => (
                      <tr key={row.when} className={`border-b last:border-0 ${preview && preview.refundPercent === row.refundPercent ? "bg-green-50 font-medium" : ""}`}>
                        <td className="p-2">{row.when}</td>
                        <td className="p-2 text-right">{row.refundPercent}% refund</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </details>
            )}

            {error && (
              <p className="text-sm text-red-600 flex gap-1 items-start">
                <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" /> {error}
              </p>
            )}
            <div className="flex gap-2">
              <button onClick={onClose} className="flex-1 py-2.5 rounded-lg border hover:bg-gray-50">
                Keep booking
              </button>
              <button
                onClick={confirm}
                disabled={busy || selectedCount < 1 || !reason}
                className="flex-1 py-2.5 rounded-lg bg-red-600 text-white font-medium hover:bg-red-700 disabled:opacity-50"
              >
                {busy ? "Cancelling..." : "Confirm cancellation"}
              </button>
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
};

export default CancelDialog;
