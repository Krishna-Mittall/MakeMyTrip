import { useState } from "react";
import { useRouter } from "next/router";
import { BedDouble, Copy, Plane, Star, XCircle } from "lucide-react";
import { fmtDateTime, inr } from "@/lib/format";
import { toast } from "@/lib/toast";
import StatusChip from "@/components/flight/StatusChip";
import EtaCountdown from "@/components/flight/EtaCountdown";
import CancelDialog from "./CancelDialog";

const statusStyle: Record<string, string> = {
  CONFIRMED: "bg-green-100 text-green-800",
  PARTIALLY_CANCELLED: "bg-amber-100 text-amber-800",
  CANCELLED: "bg-red-100 text-red-800",
};
const statusText: Record<string, string> = {
  CONFIRMED: "Confirmed",
  PARTIALLY_CANCELLED: "Partly cancelled",
  CANCELLED: "Cancelled",
};

/** The extra details and actions of a booking in the dashboard: reference, seats / room, live status, cancel, review. */
const BookingExtras = ({ booking, flightStatus, onCancelled }: { booking: any; flightStatus?: any; onCancelled: (refund: any) => void }) => {
  const router = useRouter();
  const [cancelling, setCancelling] = useState(false);
  const isFlight = booking.type === "Flight";
  const status = booking.status || "CONFIRMED";
  const travel = booking.travelDate ? new Date(booking.travelDate) : null;
  const upcoming = !travel || travel.getTime() > Date.now();
  const canCancel = status !== "CANCELLED" && upcoming && !!booking.reference;
  const remaining = booking.quantity - (booking.cancelledQuantity || 0);

  const copy = () => {
    navigator.clipboard?.writeText(booking.reference).then(() => toast({ title: "Booking reference copied", tone: "success" }));
  };

  return (
    <div className="mt-4 pt-4 border-t space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        {booking.reference && (
          <button onClick={copy} className="inline-flex items-center gap-1 text-xs font-mono px-2 py-1 rounded bg-gray-100 hover:bg-gray-200" title="Copy reference">
            {booking.reference} <Copy className="w-3 h-3" />
          </button>
        )}
        <span className={`text-xs font-medium px-2 py-1 rounded-full ${statusStyle[status] || statusStyle.CONFIRMED}`}>{statusText[status] || status}</span>
        {isFlight && flightStatus && status !== "CANCELLED" && <StatusChip status={flightStatus.status} label={flightStatus.label} />}
      </div>

      <div className="text-sm text-gray-700 space-y-1">
        {booking.title && (
          <div className="flex items-center gap-2">
            {isFlight ? <Plane className="w-4 h-4 text-blue-600" /> : <BedDouble className="w-4 h-4 text-green-600" />}
            <span className="font-medium">{booking.title}</span>
            <span className="text-gray-500">{booking.subtitle}</span>
          </div>
        )}
        {travel && (
          <div className="text-gray-600">
            {isFlight ? "Departs" : "Check-in"}: {fmtDateTime(booking.travelDate)}
            {!isFlight && booking.nights ? ` · ${booking.nights} night${booking.nights > 1 ? "s" : ""}` : ""}
          </div>
        )}
        {isFlight && booking.seats?.length > 0 && <div className="text-gray-600">Seats: {booking.seats.join(", ")}</div>}
        {!isFlight && booking.roomType && (
          <div className="text-gray-600">
            {booking.roomType} room × {remaining}
          </div>
        )}
        {booking.seatCharges > 0 && <div className="text-gray-500 text-xs">Includes {inr(booking.seatCharges)} for seat upgrades</div>}
        {booking.refundedAmount > 0 && <div className="text-green-700 text-xs">Refunded so far: {inr(booking.refundedAmount)}</div>}
      </div>

      {isFlight && flightStatus && status !== "CANCELLED" && (
        <div className="rounded-lg bg-gray-50 border p-3 text-sm">
          <div className="font-medium flex flex-wrap justify-between gap-2">
            <EtaCountdown status={flightStatus} />
            {flightStatus.gate && (
              <span className="text-gray-600">
                Gate {flightStatus.gate} · Terminal {String(flightStatus.terminal).replace("T", "")}
              </span>
            )}
          </div>
          <p className="text-gray-600 mt-1">{flightStatus.message}</p>
        </div>
      )}

      <div className="flex flex-wrap gap-2">
        {canCancel && (
          <button
            onClick={() => setCancelling(true)}
            className="inline-flex items-center gap-1 text-sm px-3 py-1.5 rounded-lg border border-red-300 text-red-600 hover:bg-red-50"
          >
            <XCircle className="w-4 h-4" /> Cancel {remaining < booking.quantity ? "remaining" : "booking"}
          </button>
        )}
        {isFlight && (
          <button
            onClick={() => router.push(`/flight-status?flight=${booking.bookingId}`)}
            className="inline-flex items-center gap-1 text-sm px-3 py-1.5 rounded-lg border hover:bg-gray-50"
          >
            <Plane className="w-4 h-4" /> Flight status
          </button>
        )}
        <button
          onClick={() => router.push(`/${isFlight ? "book-flight" : "book-hotel"}/${booking.bookingId}#reviews`)}
          className="inline-flex items-center gap-1 text-sm px-3 py-1.5 rounded-lg border hover:bg-gray-50"
        >
          <Star className="w-4 h-4" /> Rate {isFlight ? "flight" : "stay"}
        </button>
      </div>

      {cancelling && (
        <CancelDialog
          booking={booking}
          open
          onClose={() => setCancelling(false)}
          onCancelled={(r) => {
            onCancelled(r);
          }}
        />
      )}
    </div>
  );
};

export default BookingExtras;
