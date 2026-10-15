import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import RefundPolicyBlock from "@/components/cancellation/RefundPolicyBlock";
import { inr } from "@/lib/format";

type Props = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  route?: string;
  cabinBaggage?: string;
  checkInBaggage?: string;
  premiumCharge?: number;
  legroomCharge?: number;
};

const Row = ({ label, children }: { label: string; children: React.ReactNode }) => (
  <div className="flex flex-col sm:flex-row sm:gap-4 py-2 border-b last:border-0 text-sm">
    <div className="sm:w-44 shrink-0 font-medium text-gray-800">{label}</div>
    <div className="text-gray-600">{children}</div>
  </div>
);

/** The fare rules of a flight: refund tiers, changes, baggage and seat charges. */
const FareRulesDialog = ({ open, onOpenChange, route, cabinBaggage, checkInBaggage, premiumCharge = 1500, legroomCharge = 800 }: Props) => (
  <Dialog open={open} onOpenChange={onOpenChange}>
    <DialogContent className="sm:max-w-[640px] bg-white max-h-[90vh] overflow-y-auto">
      <DialogHeader>
        <DialogTitle>Fare rules{route ? ` · ${route}` : ""}</DialogTitle>
      </DialogHeader>
      <div>
        <h3 className="font-semibold mb-2">Cancellation and refund</h3>
        <RefundPolicyBlock type="flight" />
      </div>
      <div>
        <h3 className="font-semibold mb-1">Fare conditions (Economy, MMTSPECIAL)</h3>
        <Row label="Date or flight change">
          Not done directly. Cancel the booking from your dashboard (the refund tiers above apply) and book the flight you want.
        </Row>
        <Row label="Partial cancellation">You can cancel only some of the seats; the refund is worked out for those seats only.</Row>
        <Row label="Seat charges">
          Standard seats are free. Extra legroom seats cost {inr(legroomCharge)} and premium seats {inr(premiumCharge)} per seat. This amount is
          refunded together with the seat if you cancel it.
        </Row>
        <Row label="Cabin baggage">{cabinBaggage || "7 Kgs / Adult"}</Row>
        <Row label="Check-in baggage">{checkInBaggage || "15 Kgs (1 piece only) / Adult"}</Row>
        <Row label="Flight delays or cancellation by the airline">
          You are told about it in the flight status page and by notification. If the airline cancels the flight you can cancel the booking for a refund
          from your dashboard.
        </Row>
        <Row label="Price">
          The fare follows demand, holidays and how close the departure is. A price freeze (price insights) keeps today&apos;s price for the hours you choose.
        </Row>
      </div>
    </DialogContent>
  </Dialog>
);

export default FareRulesDialog;
