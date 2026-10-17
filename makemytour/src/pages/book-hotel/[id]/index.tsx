import { useRouter } from "next/router";
import {
  Star,
  MapPin,
  School as Pool,
  UtensilsCrossed,
  Wine,
  Wifi,
  Check,
  ChevronRight,
  Camera,
  CreditCard,
  Ticket,
  Plane,
  Home,
} from "lucide-react";
import { useEffect, useState } from "react";
import { gethotel, handlehotelbooking } from "@/api";
import { errorMessage, logInteraction } from "@/api/extra";
import RoomPicker from "@/components/booking/RoomPicker";
import PriceInsight from "@/components/booking/PriceInsight";
import PriceCalendar from "@/components/booking/PriceCalendar";
import ReviewSection from "@/components/reviews/ReviewSection";
import RefundPolicyBlock from "@/components/cancellation/RefundPolicyBlock";
import { toast } from "@/lib/toast";
interface Hotel {
  id: string; // Unique identifier for the hotel
  hotelName: string; // Name of the hotel
  location: string; // Location of the hotel
  pricePerNight: number; // Price per night
  availableRooms: number; // Number of available rooms
  amenities: string; // Amenities provided (comma-separated string or change to string[])
  images?: string[];
  tags?: string[];
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
import { useDispatch, useSelector } from "react-redux";
import SignupDialog from "@/components/SignupDialog";
import Loader from "@/components/Loader";
import { setUser } from "@/store";
const BookHotelPage = () => {
  const [quantity, setQuantity] = useState(1);
  const router = useRouter();
  const { id } = router.query; // Access the hotel ID from the URL
  const [hotels, sethotels] = useState<Hotel[]>([]);
  const [loading, setLoading] = useState(true);
  const user = useSelector((state: any) => state.user.user);
  const [open, setopem] = useState(false);
  const [galleryOpen, setGalleryOpen] = useState(false);
  const dispatch = useDispatch();
  const dayAfterToday = () => new Date(Date.now() + 86400000).toISOString().slice(0, 10);
  const [checkIn, setCheckIn] = useState(dayAfterToday());
  const [nights, setNights] = useState(1);
  const [roomInfo, setRoomInfo] = useState<any>(null);
  const [priceInfo, setPriceInfo] = useState<any>(null);
  const [bookingError, setBookingError] = useState("");
  const [booking, setBooking] = useState(false);
  const [reload, setReload] = useState(0);
  // tell the recommender what the traveller looked at
  useEffect(() => {
    if (user?.id && typeof id === "string") logInteraction(user.id, "VIEW", "HOTEL", id);
  }, [user?.id, id]);
  useEffect(() => {
    if (!router.isReady) return;
    const fetchhotels = async () => {
      try {
        const data = await gethotel();
        const filteredData = data.filter((hotel: any) => hotel.id === id);
        sethotels(filteredData);
      } catch (error) {
        console.error("Error fetching flights:", error);
      } finally {
        setLoading(false);
      }
    };
    fetchhotels();
  }, [router.isReady, id, reload]);

  if (loading) {
    return <Loader />;
  }
  if (hotels.length === 0) {
    return <div className="p-8 text-center text-gray-600">No hotel data available for this ID.</div>;
  }
  const hotel = hotels[0];
  const hotelData = {
    name: "Magnum Resorts- Near Candolim Beach",
    rating: 3,
    maxRating: 5,
    description: hotel
      ? `${hotel.hotelName} is in ${hotel.location}. ${hotel.tags?.length ? `Great for ${hotel.tags.join(", ")}.` : ""} Choose from ${"Standard, Deluxe and Suite"} rooms with live prices and availability.`
      : "",
    room: {
      type: "Standard Room",
      capacity: "Fits 2 Adults",
      features: [
        "No meals included",
        "10% off on food & beverage services",
        "Complimentary welcome drinks on arrival",
        "Refundable: see the cancellation & refund policy",
      ],
      originalPrice: 8999,
      discountedPrice: 664,
      taxes: 527,
    },
    location: {
      area: "Candolim",
      distance: "7 minutes walk to Candolim Beach",
    },
    reviews: {
      rating: 3.8,
      count: 784,
      text: "Very Good",
    },
  };
  // the photos and amenities of this hotel, exactly as stored
  const photos: string[] = (hotel?.images || []).filter(Boolean);
  const amenityList: string[] = (hotel?.amenities || "")
    .split(",")
    .map((a: string) => a.trim())
    .filter(Boolean);
  const amenityIcon = (name: string) => {
    const n = name.toLowerCase();
    if (n.includes("pool")) return <Pool className="w-5 h-5" />;
    if (n.includes("restaurant") || n.includes("breakfast") || n.includes("dining")) return <UtensilsCrossed className="w-5 h-5" />;
    if (n.includes("bar")) return <Wine className="w-5 h-5" />;
    if (n.includes("wi-fi") || n.includes("wifi")) return <Wifi className="w-5 h-5" />;
    return <Check className="w-5 h-5" />;
  };
  const shownStars = hotel?.reviewCount ? Math.round(hotel.avgRating || 0) : hotelData.rating;
  const ratingText = !hotel?.reviewCount
    ? "No reviews yet"
    : (hotel.avgRating || 0) >= 4.5
    ? "Excellent"
    : (hotel.avgRating || 0) >= 4
    ? "Very Good"
    : (hotel.avgRating || 0) >= 3
    ? "Good"
    : "Average";
  const handleQuantityChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    e.preventDefault();
    const value = Number.parseInt(e.target.value);
    setQuantity(
      isNaN(value)
        ? 1
        : Math.max(1, Math.min(value, roomInfo?.availableRooms ?? hotel.availableRooms))
    );
  };

  // price of the chosen room type per night (live price, price freeze already applied)
  const nightly = roomInfo?.pricePerNight ?? hotel?.pricePerNight;
  const savingPerNight = (priceInfo?.saving || 0) * (roomInfo?.priceMultiplier || 1);
  const stay = Math.max(1, nights);
  const totalPrice = (nightly + savingPerNight) * stay * quantity;
  const totalDiscounts = savingPerNight * stay * quantity;
  const totalTaxes = Math.round((totalPrice - totalDiscounts) * 0.12);
  const grandTotal = totalPrice + totalTaxes - totalDiscounts;
  const handlebooking = async (e: React.FormEvent) => {
    e.preventDefault();
    setBookingError("");
    setBooking(true);
    try {
      const data = await handlehotelbooking(
        user?.id,
        hotel?.id,
        quantity,
        grandTotal,
        roomInfo?.name,
        checkIn,
        stay,
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
  const HotelContent = () => (
    <DialogContent className="sm:max-w-[600px] bg-white">
      <DialogHeader>
        <DialogTitle className="text-2xl font-bold flex items-center">
          <Home className="w-6 h-6 mr-2" />
          Hotel Booking Details
        </DialogTitle>
      </DialogHeader>
      <div className="grid gap-6 mt-4">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Hotel Name */}
          <div className="space-y-2">
            <Label htmlFor="hotelName" className="flex items-center">
              <MapPin className="w-4 h-4 mr-2" />
              Hotel Name
            </Label>
            <Input id="hotelName" value={hotel.hotelName} readOnly />
          </div>

          {/* Room type and stay */}
          <div className="space-y-2">
            <Label htmlFor="roomType" className="flex items-center">
              <Home className="w-4 h-4 mr-2" />
              Room Type
            </Label>
            <Input id="roomType" value={roomInfo?.name || "Standard"} readOnly />
          </div>
          <div className="space-y-2">
            <Label htmlFor="stay" className="flex items-center">
              <Ticket className="w-4 h-4 mr-2" />
              Check-in / Nights
            </Label>
            <Input id="stay" value={`${checkIn} · ${stay} night${stay > 1 ? "s" : ""}`} readOnly />
          </div>

          {/* Location */}
          <div className="space-y-2">
            <Label htmlFor="location" className="flex items-center">
              <MapPin className="w-4 h-4 mr-2" />
              Location
            </Label>
            <Input id="location" value={hotel.location} readOnly />
          </div>
          {/* Price Per Night */}
          <div className="space-y-2">
            <Label htmlFor="pricePerNight" className="flex items-center">
              <Ticket className="w-4 h-4 mr-2" />
              Price Per Night
            </Label>
            <Input
              id="pricePerNight"
              value={`₹ ${Math.round(nightly).toLocaleString()}`}
              readOnly
            />
          </div>

          {/* Available Rooms */}
          <div className="space-y-2">
            <Label htmlFor="availableRooms" className="flex items-center">
              <Ticket className="w-4 h-4 mr-2" />
              Available Rooms
            </Label>
            <Input id="availableRooms" value={roomInfo?.availableRooms ?? hotel.availableRooms} readOnly />
          </div>

          {/* Number of Rooms to Book */}
          <div className="space-y-2">
            <Label htmlFor="quantity" className="flex items-center">
              <Ticket className="w-4 h-4 mr-2" />
              Number of Rooms to Book
            </Label>
            <Input
              id="quantity"
              type="number"
              min="1"
              max={roomInfo?.availableRooms ?? hotel.availableRooms}
              value={quantity}
              onChange={handleQuantityChange}
            />
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
            <div className="flex justify-between items-center">
              <span className="text-gray-600">Taxes and Extracharges (12%)</span>
              <span className="font-medium">
                ₹ {totalTaxes.toLocaleString()}
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
    <div className="min-h-screen bg-gray-50">
      {/* Breadcrumb */}
      <div className="bg-white border-b">
        <div className="max-w-7xl mx-auto px-4 py-3">
          <div className="flex items-center space-x-2 text-sm">
            <a href="/" className="text-blue-500">
              Home
            </a>
            <ChevronRight className="w-4 h-4 text-gray-400" />
            <a href="/" className="text-blue-500">
              {hotel?.location}
            </a>
            <ChevronRight className="w-4 h-4 text-gray-400" />
            <span className="text-gray-600">{hotel?.hotelName}</span>
          </div>
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Main Content */}
          <div className="lg:col-span-2">
            {/* Hotel Title & Rating */}
            <div className="mb-6">
              <h1 className="text-2xl font-bold mb-2">{hotel.hotelName}</h1>
              <div className="flex items-center space-x-1">
                {[...Array(shownStars)].map((_, i) => (
                  <Star
                    key={i}
                    className="w-5 h-5 text-yellow-400 fill-current"
                  />
                ))}
                {[...Array(hotelData.maxRating - shownStars)].map(
                  (_, i) => (
                    <Star key={i} className="w-5 h-5 text-gray-300" />
                  )
                )}
              </div>
            </div>

            {/* Image Gallery */}
            <div className="grid grid-cols-3 gap-4 mb-8">
              <div className="col-span-2 relative group cursor-pointer">
                <img
                  src={hotel.images?.[0] || "https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=800"}
                  alt="Hotel Main"
                  className="w-full h-80 object-cover rounded-lg"
                />
                {photos.length > 0 && (
                  <button
                    type="button"
                    onClick={() => setGalleryOpen(true)}
                    className="absolute bottom-4 left-4 bg-white/90 px-3 py-1 rounded-full flex items-center space-x-1 hover:bg-white"
                  >
                    <Camera className="w-4 h-4" />
                    <span className="text-sm">
                      {photos.length} {photos.length === 1 ? "photo" : "photos"}
                    </span>
                  </button>
                )}
              </div>
              <div className="space-y-4">
                <div className="relative group cursor-pointer">
                  <img
                    src={hotel.images?.[1] || "https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=800"}
                    alt="Hotel Room"
                    className="w-full h-[152px] object-cover rounded-lg"
                  />
                </div>
                <div className="relative group cursor-pointer">
                  <img
                    src={hotel.images?.[2] || "https://images.unsplash.com/photo-1587474260584-136574528ed5?auto=format&fit=crop&w=800"}
                    alt="Hotel Amenity"
                    className="w-full h-[152px] object-cover rounded-lg"
                  />
                </div>
              </div>
            </div>

            <Dialog open={galleryOpen} onOpenChange={setGalleryOpen}>
              <DialogContent className="sm:max-w-[800px] bg-white max-h-[90vh] overflow-y-auto">
                <DialogHeader>
                  <DialogTitle>{hotel.hotelName} - photos</DialogTitle>
                </DialogHeader>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {photos.map((src: string, i: number) => (
                    <img key={i} src={src} alt={`${hotel.hotelName} photo ${i + 1}`} className="w-full h-56 object-cover rounded-lg" />
                  ))}
                </div>
              </DialogContent>
            </Dialog>

            {/* Description */}
            <p className="text-gray-600 mb-6">
              {hotelData.description}
            </p>

            {/* Amenities */}
            <div className="mb-8">
              <h2 className="text-xl font-semibold mb-4">Amenities</h2>
              <div className="flex flex-wrap gap-6">
                {amenityList.length === 0 && <p className="text-gray-500 text-sm">No amenities listed for this hotel.</p>}
                {amenityList.map((name) => (
                  <div key={name} className="flex items-center space-x-2 text-gray-600">
                    {amenityIcon(name)}
                    <span>{name}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Choose a room */}
            <div className="mb-8">
              <h2 className="text-xl font-semibold mb-4">Choose your room</h2>
              <div className="flex flex-wrap gap-4 mb-4 bg-white rounded-xl border p-3">
                <label className="text-sm text-gray-600">
                  Check-in date
                  <input
                    type="date"
                    min={new Date().toISOString().slice(0, 10)}
                    value={checkIn}
                    onChange={(e) => e.target.value && setCheckIn(e.target.value)}
                    className="block mt-1 border rounded-md px-2 py-1.5 text-gray-900"
                  />
                </label>
                <label className="text-sm text-gray-600">
                  Nights
                  <input
                    type="number"
                    min={1}
                    max={30}
                    value={nights}
                    onChange={(e) => setNights(Math.max(1, Math.min(30, parseInt(e.target.value) || 1)))}
                    className="block mt-1 border rounded-md px-2 py-1.5 w-24 text-gray-900"
                  />
                </label>
              </div>
              <PriceCalendar hotelId={hotel.id} selected={checkIn} onPick={setCheckIn} />
              <RoomPicker
                hotelId={hotel.id}
                stayDate={checkIn}
                selected={roomInfo?.name || ""}
                onSelect={(room: any) => {
                  setRoomInfo(room);
                  setQuantity((q) => Math.max(1, Math.min(q, room.availableRooms || 1)));
                }}
              />
            </div>

            {/* Live price, history and price freeze */}
            <div className="mb-8 bg-white rounded-xl shadow-sm p-4 sm:p-6">
              <h2 className="text-xl font-semibold mb-4">Price insights</h2>
              <PriceInsight type="HOTEL" itemId={hotel.id} stayDate={checkIn} onQuote={setPriceInfo} />
            </div>

            {/* Cancellation policy */}
            <div className="mb-8">
              <h2 className="text-xl font-semibold mb-4">Cancellation & refund policy</h2>
              <RefundPolicyBlock type="hotel" />
            </div>

            <ReviewSection
              targetType="HOTEL"
              targetId={hotel.id}
              targetName={hotel.hotelName}
              onChanged={() => setReload((n) => n + 1)}
            />
          </div>

          {/* Booking Card */}
          <div className="lg:col-span-1">
            <div className="bg-white rounded-xl shadow-lg p-6">
              <h3 className="text-xl font-semibold mb-4">
                {roomInfo ? `${roomInfo.name} Room` : hotelData.room.type}
              </h3>
              <p className="text-gray-600 mb-4">
                {roomInfo ? `Fits ${roomInfo.capacity} guests · ${roomInfo.bedType}` : hotelData.room.capacity}
              </p>

              <ul className="space-y-3 mb-6">
                {(roomInfo?.features || hotelData.room.features).map((feature: string, index: number) => (
                  <li key={index} className="flex items-start space-x-2">
                    <span className="text-gray-400">•</span>
                    <span className="text-gray-600">{feature}</span>
                  </li>
                ))}
              </ul>
              <div className="mb-6">
                {/* Price Per Night */}
                <div className="flex items-center justify-between mb-2">
                  <span className="text-gray-800 font-semibold">
                    Price Per Night:
                  </span>
                  <span className="text-lg font-medium text-gray-800">
                    ₹ {Math.round(nightly).toLocaleString()}
                  </span>
                </div>

                {/* Available Rooms */}
                <div className="flex items-center justify-between mb-4">
                  <span className="text-gray-800 font-semibold">
                    Available Rooms:
                  </span>
                  <span className="text-lg font-medium text-gray-800">
                    {roomInfo?.availableRooms ?? hotel.availableRooms}
                  </span>
                </div>

                {/* Amenities */}
                <div>
                  <h4 className="text-gray-800 font-semibold mb-2">
                    Amenities:
                  </h4>
                  <p className="text-gray-600">{hotel.amenities}</p>
                </div>
              </div>
              <div className="space-y-2 mb-6">
                <div className="flex items-center justify-between">
                  {hotel.pricePerNight > nightly ? (
                    <span className="text-gray-500 line-through">₹ {Math.round(hotel.pricePerNight).toLocaleString()}</span>
                  ) : (
                    <span />
                  )}
                  <span className="text-gray-500">
                    {stay} night{stay > 1 ? "s" : ""} × {quantity} room{quantity > 1 ? "s" : ""}
                  </span>
                </div>
                <div className="flex items-center justify-between text-2xl font-bold">
                  <span>₹ {grandTotal.toLocaleString()}</span>
                  <span className="text-sm text-gray-500 font-normal">
                    incl. ₹ {totalTaxes.toLocaleString()} taxes & fees
                  </span>
                </div>
              </div>
              <Dialog open={open} onOpenChange={setopem}>
                <DialogTrigger asChild>
                  <button className="w-full bg-blue-500 text-white py-3 rounded-lg hover:bg-blue-600 transition-colors mb-3">
                    BOOK THIS NOW
                  </button>
                </DialogTrigger>
                {user ? (
                  <HotelContent />
                ) : (
                  <DialogContent className="bg-white">
                    <DialogHeader>
                      <DialogTitle>Login Required</DialogTitle>
                    </DialogHeader>
                    <p>Please log in to continue with your booking.</p>
                    <SignupDialog
                      redirectHome={false}
                      trigger={
                        <Button className="w-full">Log In / Sign Up</Button>
                      }
                    />
                  </DialogContent>
                )}
              </Dialog>
            </div>

            {/* Rating Card */}
            <div className="bg-white rounded-xl shadow-lg p-6 mt-6">
              <div className="flex items-center justify-between mb-4">
                <div className="flex items-center space-x-4">
                  <div className="bg-blue-500 text-white text-2xl font-bold w-16 h-16 rounded-lg flex items-center justify-center">
                    {hotel.reviewCount ? hotel.avgRating?.toFixed(1) : "New"}
                  </div>
                  <div>
                    <div className="font-semibold text-lg">{ratingText}</div>
                    <div className="text-gray-500">
                      ({hotel.reviewCount || 0} ratings)
                    </div>
                  </div>
                </div>
                <a href="#reviews" className="text-blue-500">
                  All Reviews
                </a>
              </div>
            </div>

            {/* Location Card */}
            <div className="bg-white rounded-xl shadow-lg p-6 mt-6">
              <div className="flex items-start justify-between">
                <div>
                  <h3 className="font-semibold text-lg mb-1">
                    {hotel.location}
                  </h3>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default BookHotelPage;
