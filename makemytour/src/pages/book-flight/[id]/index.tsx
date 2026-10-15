import { useRouter } from "next/router";

import {
  Plane,
  Luggage,
  Clock,
  Calendar,
  MapPin,
  Gift,
  CreditCard,
  AlertCircle,
  ChevronRight,
  Star,
  Info,
  ArrowRight,
} from "lucide-react";
import { useEffect, useState } from "react";
import { getflight, handleflightbooking } from "@/api";
import { errorMessage, getFlightStatus, logInteraction, trackFlight, untrackFlight } from "@/api/extra";
import SeatMap from "@/components/booking/SeatMap";
import PriceInsight from "@/components/booking/PriceInsight";
import ReviewSection from "@/components/reviews/ReviewSection";
import RefundPolicyBlock from "@/components/cancellation/RefundPolicyBlock";
import StatusChip from "@/components/flight/StatusChip";
import EtaCountdown from "@/components/flight/EtaCountdown";
import { useEvents } from "@/hooks/useEvents";
import { toast } from "@/lib/toast";
import { fmtTime, inr } from "@/lib/format";
import { useDispatch, useSelector } from "react-redux";
interface Flight {
  id: string; // Unique identifier for the flight
  flightName: string; // Name of the flight
  from: string; // Departure location
  to: string; // Arrival location
  departureTime: string; // Departure time (ISO 8601 string recommended)
  arrivalTime: string; // Arrival time (ISO 8601 string recommended)
  price: number; // Price of the flight
  availableSeats: number; // Number of available seats
  currentPrice?: number; // Price after dynamic pricing
  avgRating?: number;
  reviewCount?: number;
}
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Users, Ticket } from "lucide-react";
import SignupDialog from "@/components/SignupDialog";
import Loader from "@/components/Loader";
import { setUser } from "@/store";
const BookFlightPage = () => {
  const router = useRouter();
  const { id } = router.query;
  const [flights, setFlights] = useState<Flight[]>([]);
  const [loading, setLoading] = useState(true);
  const [quantity, setQuantity] = useState(1);
  const [open, setopem] = useState(false);
  const user = useSelector((state: any) => state.user.user);
  const dispatch = useDispatch();
  const [selectedSeats, setSelectedSeats] = useState<string[]>([]);
  const [seatCharges, setSeatCharges] = useState(0);
  const [priceInfo, setPriceInfo] = useState<any>(null);
  const [status, setStatus] = useState<any>(null);
  const [bookingError, setBookingError] = useState("");
  const [booking, setBooking] = useState(false);

  // the live status of this flight (and updates while the page is open)
  useEffect(() => {
    if (typeof id === "string") getFlightStatus(id, user?.id).then(setStatus).catch(() => null);
  }, [id, user?.id]);
  useEvents(["FLIGHT_STATUS"], (s) => {
    if (s?.flightId === id) setStatus((old: any) => ({ ...old, ...s }));
  });
  // tell the recommender what the traveller looked at
  useEffect(() => {
    if (user?.id && typeof id === "string") logInteraction(user.id, "VIEW", "FLIGHT", id);
  }, [user?.id, id]);
  useEffect(() => {
    const fetchFlights = async () => {
      try {
        const data = await getflight();
        const filteredData = data.filter((flight: any) => flight.id === id);
        setFlights(filteredData);
        console.log(filteredData);
      } catch (error) {
        console.error("Error fetching flights:", error);
      } finally {
        setLoading(false);
      }
    };
    fetchFlights();
  }, [id, user]);

  if (loading) {
    return <Loader />;
  }
  if (flights.length === 0) {
    return <div>No flight data available for this ID.</div>;
  }
  const flight = flights[0];
  const flightDetails = {
    from: "Bengaluru",
    to: "New Delhi",
    date: "Thursday, Jan 16",
    flightNo: "IX 2747",
    aircraft: "Airbus A320",
    airline: "Air India Express",
    departureTime: "17:55",
    arrivalTime: "20:55",
    duration: "3h 0m",
    departureTerminal: "Bengaluru International Airport, Terminal T2",
    arrivalTerminal: "Indira Gandhi International Airport, Terminal T3",
    cabinBaggage: "7 Kgs / Adult",
    checkInBaggage: "15 Kgs (1 piece only) / Adult",
  };

  const fareSummary = {
    baseFare: 6124,
    taxes: 1374,
    otherServices: 249,
    discounts: -250,
    total: 7497,
  };

  const promoOffers = [
    {
      code: "MMTSECURE",
      description:
        "Get an instant discount of ₹299 on your flight booking and Trip Secure with this coupon!",
      amount: 299,
    },
    {
      code: "SPECIALUPI",
      description:
        "Use this code and get ₹362 instant discount on payments via UPI only!",
      amount: 362,
    },
  ];

  const hotels = [
    {
      name: "Hotel Park Tree",
      rating: 4,
      price: 9000,
      image:
        "https://images.unsplash.com/photo-1571896349842-33c89424de2d?auto=format&fit=crop&w=800",
      location: "Near Airport, New Delhi",
    },
    {
      name: "Lemon Tree Premier",
      rating: 4,
      price: 43875,
      image:
        "https://images.unsplash.com/photo-1542314831-068cd1dbfeeb?auto=format&fit=crop&w=800",
      location: "Connaught Place, New Delhi",
    },
    {
      name: "Hotel Kian",
      rating: 4,
      price: 1968,
      image:
        "https://images.unsplash.com/photo-1618773928121-c32242e63f39?auto=format&fit=crop&w=800",
      location: "Karol Bagh, New Delhi",
    },
  ];
  const formatDate = (dateString: string): string => {
    const options: Intl.DateTimeFormatOptions = {
      year: "numeric",
      month: "long",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    };
    const date = new Date(dateString);
    return date.toLocaleString("en-US", options);
  };

  const handleQuantityChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    e.preventDefault();
    const value = Number.parseInt(e.target.value);
    setQuantity(
      isNaN(value) ? 1 : Math.max(1, Math.min(value, flight.availableSeats))
    );
  };

  // the number of tickets follows the selected seats; the fare uses the live (dynamic) price
  const qty = selectedSeats.length > 0 ? selectedSeats.length : quantity;
  const currentUnit = priceInfo?.current ?? flight?.currentPrice ?? flight?.price;
  const totalPrice = currentUnit * qty;
  const totalTaxes = fareSummary?.taxes * qty;
  const totalOtherServices = fareSummary?.otherServices * qty;
  const totalDiscounts = (priceInfo?.saving || 0) * qty;
  const grandTotal =
    totalPrice + seatCharges + totalTaxes + totalOtherServices - totalDiscounts;
  const durationMinutes = Math.round(
    (new Date(flight.arrivalTime).getTime() - new Date(flight.departureTime).getTime()) / 60000
  );
  flightDetails.duration = `${Math.floor(durationMinutes / 60)}h ${durationMinutes % 60}m`;

  const handlebooking = async (e: React.FormEvent) => {
    e.preventDefault();
    setBookingError("");
    setBooking(true);
    try {
      const data = await handleflightbooking(
        user?.id,
        flight?.id,
        qty,
        grandTotal,
        selectedSeats,
        priceInfo?.freezeId
      );
      const updateuser = {
        ...user,
        bookings: [...user.bookings, data],
      };
      dispatch(setUser(updateuser));
      setopem(false);
      setQuantity(1);
      toast({ title: "Booking confirmed", message: `Reference ${data.reference}`, tone: "success" });
      router.push("/profile");
    } catch (error) {
      setBookingError(errorMessage(error));
    } finally {
      setBooking(false);
    }
  };
  const BookingContent = () => (
    <DialogContent className="sm:max-w-[600px] bg-white">
      <DialogHeader>
        <DialogTitle className="text-2xl font-bold flex items-center">
          <Plane className="w-6 h-6 mr-2" />
          Flight Booking Details
        </DialogTitle>
      </DialogHeader>
      <div className="grid gap-6 mt-4">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="flightName" className="flex items-center">
              <Plane className="w-4 h-4 mr-2" />
              Flight Name
            </Label>
            <Input id="flightName" value={flight?.flightName} readOnly />
          </div>
          <div className="space-y-2">
            <Label htmlFor="from" className="flex items-center">
              <MapPin className="w-4 h-4 mr-2" />
              From
            </Label>
            <Input id="from" value={flight?.from} readOnly />
          </div>
          <div className="space-y-2">
            <Label htmlFor="to" className="flex items-center">
              <MapPin className="w-4 h-4 mr-2" />
              To
            </Label>
            <Input id="to" value={flight?.to} readOnly />
          </div>
          <div className="space-y-2">
            <Label htmlFor="departureTime" className="flex items-center">
              <Calendar className="w-4 h-4 mr-2" />
              Departure Time
            </Label>
            <Input
              id="departureTime"
              value={new Date(flight.departureTime).toLocaleString()}
              readOnly
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="arrivalTime" className="flex items-center">
              <Clock className="w-4 h-4 mr-2" />
              Arrival Time
            </Label>
            <Input
              id="arrivalTime"
              value={new Date(flight.arrivalTime).toLocaleString()}
              readOnly
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="quantity" className="flex items-center">
              <Ticket className="w-4 h-4 mr-2" />
              Number of Tickets
            </Label>
            <Input
              id="quantity"
              type="number"
              min="1"
              max={flight.availableSeats}
              value={qty}
              readOnly={selectedSeats.length > 0}
              onChange={handleQuantityChange}
            />
            {selectedSeats.length > 0 && (
              <p className="text-xs text-gray-500">Seats: {selectedSeats.join(", ")}</p>
            )}
          </div>
        </div>
        <div className="bg-gray-100 rounded-lg p-4">
          <h3 className="text-lg font-bold mb-4 flex items-center">
            <CreditCard className="w-5 h-5 mr-2" />
            Fare Summary
          </h3>
          <div className="space-y-2">
            <div className="flex justify-between items-center">
              <span className="text-gray-600">Base Fare</span>
              <span className="font-medium">
                ₹ {totalPrice.toLocaleString()}
              </span>
            </div>
            {seatCharges > 0 && (
              <div className="flex justify-between items-center">
                <span className="text-gray-600">Seat upgrades</span>
                <span className="font-medium">₹ {seatCharges.toLocaleString()}</span>
              </div>
            )}
            <div className="flex justify-between items-center">
              <span className="text-gray-600">Taxes and Surcharges</span>
              <span className="font-medium">
                ₹ {totalTaxes.toLocaleString()}
              </span>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-gray-600">Other Services</span>
              <span className="font-medium">
                ₹ {totalOtherServices.toLocaleString()}
              </span>
            </div>
            <div className="flex justify-between items-center text-green-600">
              <span className="font-medium">Discounts</span>
              <span className="font-medium">
                - ₹ {Math.abs(totalDiscounts).toLocaleString()}
              </span>
            </div>
            <div className="border-t pt-2 mt-2">
              <div className="flex justify-between items-center">
                <span className="font-bold text-lg">Total Amount</span>
                <span className="font-bold text-lg">
                  ₹ {grandTotal.toLocaleString()}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
      {bookingError && (
        <p className="text-sm text-red-600 bg-red-50 rounded-lg p-3" role="alert">
          {bookingError}
        </p>
      )}
      <Button className="w-full mt-4" onClick={handlebooking} disabled={booking}>
        {booking ? "Booking..." : "Proceed to Payment"}
      </Button>
    </DialogContent>
  );
  return (
    <div className="min-h-screen bg-[#f4f7fa]">
      <div className="max-w-7xl mx-auto px-4 py-8">
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Main Content */}
          <div className="lg:col-span-2 space-y-6">
            {/* Flight Details */}
            <div className="bg-white rounded-xl shadow-sm p-6">
              <div className="flex flex-wrap justify-between items-start gap-4 mb-6">
                <div>
                  <div className="flex items-center flex-wrap gap-4 mb-2">
                    <h2 className="text-lg font-bold flex items-center">
                      <span>{flight?.from}</span>
                      <ArrowRight className="w-5 h-5 mx-2" />
                      <span>{flight?.to}</span>
                    </h2>
                    <span className="bg-green-100 text-green-600 text-xs px-3 py-1 rounded-full font-medium">
                      CANCELLATION FEES APPLY
                    </span>
                  </div>
                  <div className="flex items-center text-sm text-gray-600">
                    <Calendar className="w-4 h-4 mr-2" />
                    <span>{formatDate(flight.departureTime)}</span>
                    <span className="mx-2">•</span>
                    <Clock className="w-4 h-4 mr-2" />
                    <span>Non Stop - {flightDetails.duration}</span>
                  </div>
                </div>
                <button className="text-blue-600 text-sm font-medium hover:text-blue-700 flex items-center">
                  <Info className="w-4 h-4 mr-1" />
                  View Fare Rules
                </button>
              </div>

              <div className="flex items-center space-x-4 mb-6">
                <div className="w-12 h-12 bg-blue-100 rounded-full flex items-center justify-center">
                  <Plane className="w-6 h-6 text-blue-600" />
                </div>
                <div>
                  <div className="font-semibold">{flight.flightName}</div>
                  <div className="text-sm text-gray-600">
                    {flightDetails.flightNo} • {flightDetails.aircraft}
                  </div>
                </div>
                <div className="ml-auto text-sm">
                  <span className="px-3 py-1 bg-blue-50 text-blue-600 rounded-full">
                    Economy
                  </span>
                  <span className="ml-2 text-gray-600">MMTSPECIAL</span>
                </div>
              </div>

              {status && (
                <div className="mb-6 rounded-xl border bg-gray-50 p-4">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <StatusChip status={status.status} label={status.label} size="md" />
                    <span className="text-sm font-medium text-gray-700">
                      <EtaCountdown status={status} />
                    </span>
                  </div>
                  <p className="text-sm text-gray-600 mt-2">{status.message}</p>
                  {status.delayMinutes > 0 && (
                    <p className="text-xs text-gray-500 mt-1">
                      Scheduled {fmtTime(status.scheduledDeparture)} → revised {fmtTime(status.estimatedDeparture)} (arrival {fmtTime(status.estimatedArrival)})
                    </p>
                  )}
                  <div className="flex flex-wrap items-center gap-3 mt-3">
                    <button
                      className={`text-sm px-3 py-1.5 rounded-full border ${status.tracked ? "bg-blue-600 text-white border-blue-600" : "bg-white text-blue-700 border-blue-300 hover:bg-blue-50"}`}
                      onClick={async () => {
                        if (!user?.id) {
                          toast({ title: "Log in to track this flight", message: "Use the Login button at the top right.", tone: "warning" });
                          return;
                        }
                        try {
                          if (status.tracked) await untrackFlight(user.id, flight.id);
                          else await trackFlight(user.id, flight.id);
                          setStatus((old: any) => ({ ...old, tracked: !old.tracked }));
                        } catch (e) {
                          toast({ title: errorMessage(e), tone: "error" });
                        }
                      }}
                    >
                      {status.tracked ? "Tracking - you will get alerts" : "Track this flight"}
                    </button>
                    <button className="text-sm text-blue-600 hover:underline" onClick={() => router.push(`/flight-status?flight=${flight.id}`)}>
                      Open live status
                    </button>
                  </div>
                </div>
              )}

              <div className="flex flex-wrap md:flex-nowrap justify-between items-start gap-6 border-t pt-6">
                <div>
                  <div className="text-2xl font-bold">
                    {formatDate(flight.departureTime)}
                  </div>
                  <div className="text-sm text-gray-600 mt-1 flex items-start">
                    <MapPin className="w-4 h-4 mr-1 flex-shrink-0 mt-0.5" />
                    {flight.from} International Airport, Terminal T2
                  </div>
                </div>
                <div className="text-center flex-shrink-0">
                  <div className="text-sm text-gray-600 mb-1">
                    {flightDetails.duration}
                  </div>
                  <div className="w-32 h-0.5 bg-gray-300 relative my-2">
                    <div className="absolute -top-2 right-0 w-4 h-4 rounded-full bg-gray-300 flex items-center justify-center">
                      <Plane className="w-3 h-3 text-gray-600" />
                    </div>
                  </div>
                  <div className="text-xs text-gray-500">Non-stop</div>
                </div>
                <div className="text-right">
                  <div className="text-2xl font-bold">
                    {formatDate(flight.arrivalTime)}
                  </div>
                  <div className="text-sm text-gray-600 mt-1 flex items-start justify-end">
                    <MapPin className="w-4 h-4 mr-1 flex-shrink-0 mt-0.5" />
                    {flight.to} International Airport, Terminal T3
                  </div>
                </div>
              </div>

              <div className="flex flex-wrap gap-6 mt-6 text-sm text-gray-600">
                <div className="flex items-center">
                  <Luggage className="w-5 h-5 mr-2 text-gray-500" />
                  <span>Cabin Baggage: {flightDetails.cabinBaggage}</span>
                </div>
                <div className="flex items-center">
                  <Luggage className="w-5 h-5 mr-2 text-gray-500" />
                  <span>Check-in Baggage: {flightDetails.checkInBaggage}</span>
                </div>
              </div>
            </div>

            {/* Cancellation Policy */}
            <div className="bg-white rounded-xl shadow-sm p-6">
              <div className="flex justify-between items-center mb-6">
                <h2 className="text-lg font-bold flex items-center">
                  <AlertCircle className="w-5 h-5 mr-2 text-orange-500" />
                  Cancellation & Date Change Policy
                </h2>
                <button className="text-blue-600 text-sm font-medium hover:text-blue-700">
                  View Policy
                </button>
              </div>
              <RefundPolicyBlock type="flight" />
            </div>

            {/* Choose your seats */}
            <div className="bg-white rounded-xl shadow-sm p-4 sm:p-6">
              <h2 className="text-lg font-bold mb-4 flex items-center">
                Choose your seats
                <span className="ml-2 text-xs font-normal text-gray-500">(optional - we pick seats for you if you skip this)</span>
              </h2>
              <SeatMap
                flightId={flight.id}
                selected={selectedSeats}
                onChange={(seats, charge) => {
                  setSelectedSeats(seats);
                  setSeatCharges(charge);
                }}
              />
            </div>

            {/* Live price, history and price freeze */}
            <div className="bg-white rounded-xl shadow-sm p-4 sm:p-6">
              <h2 className="text-lg font-bold mb-4">Price insights</h2>
              <PriceInsight type="FLIGHT" itemId={flight.id} onQuote={setPriceInfo} />
            </div>

            {/* Hotel Offers */}
            <div className="bg-white rounded-xl shadow-sm p-6">
              <div className="flex items-center justify-between mb-6">
                <h2 className="text-lg font-bold flex items-center">
                  <Gift className="w-5 h-5 mr-2 text-red-500" />
                  Book a Flight & unlock these offers
                </h2>
                <span className="bg-red-100 text-red-600 text-xs px-3 py-1 rounded-full font-medium">
                  Flyer Exclusive Deal
                </span>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                {hotels.map((hotel, index) => (
                  <div
                    key={index}
                    className="bg-white border rounded-xl overflow-hidden hover:shadow-md transition-shadow"
                  >
                    <div className="relative">
                      <img
                        src={hotel.image}
                        alt={hotel.name}
                        className="w-full h-48 object-cover"
                      />
                      <div className="absolute top-3 right-3 bg-white px-2 py-1 rounded-full text-xs font-medium">
                        Best Seller
                      </div>
                    </div>
                    <div className="p-4">
                      <h3 className="font-semibold text-lg mb-1">
                        {hotel.name}
                      </h3>
                      <div className="flex items-center text-sm text-gray-600 mb-2">
                        <MapPin className="w-4 h-4 mr-1" />
                        {hotel.location}
                      </div>
                      <div className="flex items-center justify-between">
                        <div className="flex items-center text-yellow-400">
                          {[...Array(hotel.rating)].map((_, i) => (
                            <Star key={i} className="w-4 h-4 fill-current" />
                          ))}
                        </div>
                        <div className="text-right">
                          <div className="text-xs text-gray-500">
                            Starting from
                          </div>
                          <div className="font-bold text-lg">
                            ₹ {hotel.price.toLocaleString()}
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Ratings and reviews */}
            <ReviewSection targetType="FLIGHT" targetId={flight.id} targetName={flight.flightName} />
          </div>

          {/* Fare Summary */}
          <div className="lg:col-span-1">
            <div className="bg-white rounded-xl shadow-sm p-6 sticky top-24">
              <h2 className="text-lg font-bold mb-6 flex items-center">
                <CreditCard className="w-5 h-5 mr-2 text-gray-600" />
                Fare Summary
              </h2>
              <div className="space-y-2">
                <div className="flex justify-between items-center">
                  <span className="text-gray-600">Base Fare</span>
                  <span className="font-medium">
                    ₹ {totalPrice.toLocaleString()}
                  </span>
                </div>
                {seatCharges > 0 && (
                  <div className="flex justify-between items-center">
                    <span className="text-gray-600">Seat upgrades</span>
                    <span className="font-medium">₹ {seatCharges.toLocaleString()}</span>
                  </div>
                )}
                <div className="flex justify-between items-center">
                  <span className="text-gray-600">Taxes and Surcharges</span>
                  <span className="font-medium">
                    ₹ {totalTaxes.toLocaleString()}
                  </span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-gray-600">Other Services</span>
                  <span className="font-medium">
                    ₹ {totalOtherServices.toLocaleString()}
                  </span>
                </div>
                <div className="flex justify-between items-center text-green-600">
                  <span className="font-medium">Discounts</span>
                  <span className="font-medium">
                    - ₹ {Math.abs(totalDiscounts).toLocaleString()}
                  </span>
                </div>
                <div className="border-t pt-2 mt-2">
                  <div className="flex justify-between items-center">
                    <span className="font-bold text-lg">Total Amount</span>
                    <span className="font-bold text-lg">
                      ₹ {grandTotal.toLocaleString()}
                    </span>
                  </div>
                </div>
              </div>
              <Dialog open={open} onOpenChange={setopem}>
                <DialogTrigger asChild>
                  <Button className="w-full bg-red-600 text-white">
                    Book Now
                  </Button>
                </DialogTrigger>
                {user ? (
                  <BookingContent />
                ) : (
                  <DialogContent className="bg-white">
                    <DialogHeader>
                      <DialogTitle>Login Required</DialogTitle>
                    </DialogHeader>
                    <p>Please log in to continue with your booking.</p>
                    <SignupDialog
                      trigger={
                        <Button className="w-full">Log In / Sign Up</Button>
                      }
                    />
                  </DialogContent>
                )}
              </Dialog>
              {/* Promo Codes */}
              <div className="mt-8">
                <div className="bg-[#FFF8E7] p-6 rounded-xl">
                  <h3 className="font-bold mb-4 flex items-center">
                    <Gift className="w-5 h-5 mr-2 text-yellow-600" />
                    PROMO CODES
                  </h3>
                  <div className="relative mb-4">
                    <input
                      type="text"
                      placeholder="Enter promo code here"
                      className="w-full px-4 py-3 border border-gray-200 rounded-lg focus:ring-2 focus:ring-red-500 focus:border-red-500 transition-colors"
                    />
                  </div>
                  {promoOffers.map((offer, index) => (
                    <div
                      key={index}
                      className="bg-white p-4 rounded-lg mb-3 shadow-sm"
                    >
                      <div className="flex items-start gap-3">
                        <input
                          type="radio"
                          name="promo"
                          className="mt-1.5 h-4 w-4 text-red-600 focus:ring-red-500"
                        />
                        <div>
                          <div className="font-semibold text-red-600">
                            {offer.code}
                          </div>
                          <p className="text-sm text-gray-600 mt-1">
                            {offer.description}
                          </p>
                          <button className="text-blue-600 text-sm font-medium mt-2 hover:text-blue-700">
                            Terms & Conditions
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default BookFlightPage;
