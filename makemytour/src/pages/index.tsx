import { getflight, gethotel } from "@/api";
import { logInteraction } from "@/api/extra";
import Recommendations from "@/components/recommendations/Recommendations";
import PriceAlerts from "@/components/booking/PriceAlerts";
import StatusChip from "@/components/flight/StatusChip";
import { useEvents } from "@/hooks/useEvents";
import Loader from "@/components/Loader";
import { SearchSelect } from "@/components/SearchSelect";
import SignupDialog from "@/components/SignupDialog";
import { Button } from "@/components/ui/button";
import {
  Bus,
  Calendar,
  Car,
  CreditCard,
  HomeIcon,
  Hotel,
  MapPin,
  Plane,
  Shield,
  Train,
  Umbrella,
  Users,
} from "lucide-react";
import { toast } from "@/lib/toast";
import { useRouter } from "next/router";
import { useEffect, useMemo, useRef, useState } from "react";
import { useSelector } from "react-redux";

export default function Home() {
  const [bookingtype, setbookingtype] = useState("flights");
  const comingSoon = (what: string) =>
    toast({ title: `${what} - coming soon`, message: "This demo covers flights and hotels. Please use the Flights or Hotels tab.", tone: "info" });
  const [from, setfrom] = useState("");
  const [to, setto] = useState("");
  const [date, setdate] = useState("");
  const [travelers, settravelers] = useState(1);
  const [searchresults, setsearchresult] = useState<any[]>([]);
  const [hotel, sethotel] = useState<any[]>([]);
  const [loading, setloading] = useState(true);
  const [flight, setflight] = useState<any[]>([]);
  const [searched, setsearched] = useState(false);
  const [formError, setformError] = useState("");
  const [notice, setnotice] = useState("");
  const dateRef = useRef<HTMLInputElement>(null);
  const [searching, setsearching] = useState(false);
  const [suggested, setsuggested] = useState(false);
  const user = useSelector((state: any) => state.user.user);
  const router = useRouter();

  // live price updates pushed by the pricing engine
  useEvents(["PRICE_UPDATE"], (changes) => {
    if (!Array.isArray(changes)) return;
    const apply = (list: any[], type: string, priceKey: string) =>
      list.map((item) => {
        const c = changes.find((x: any) => x.type === type && x.id === item.id);
        return c ? { ...item, [priceKey]: c.price, priceTrend: c.trend, priceChangePercent: c.changePercent } : item;
      });
    setflight((list) => apply(list, "FLIGHT", "currentPrice"));
    sethotel((list) => apply(list, "HOTEL", "currentPricePerNight"));
    setsearchresult((list) => (bookingtype === "flights" ? apply(list, "FLIGHT", "currentPrice") : apply(list, "HOTEL", "currentPricePerNight")));
  });
  const flightD = [
    { id: 1, from: "Delhi", to: "Mumbai", date: "2025-01-15", price: 5000 },
    { id: 2, from: "Mumbai", to: "Bengaluru", date: "2025-01-16", price: 4500 },
    { id: 3, from: "Bengaluru", to: "Delhi", date: "2025-01-17", price: 5500 },
    { id: 4, from: "Delhi", to: "Kolkata", date: "2025-01-18", price: 6000 },
  ];

  const hotelData = [
    { id: 1, name: "Luxury Palace", city: "Mumbai", price: 15000 },
    { id: 2, name: "Comfort Inn", city: "Delhi", price: 8000 },
    { id: 3, name: "Seaside Resort", city: "Goa", price: 12000 },
    { id: 4, name: "Mountain View Hotel", city: "Shimla", price: 10000 },
  ];
  useEffect(() => {
    const fetchdata = async () => {
      try {
        // both lists are loaded at the same time so the page appears sooner
        const [hotelData, flightdata] = await Promise.all([gethotel(), getflight()]);
        sethotel(hotelData);
        setflight(flightdata);
      } catch (error) {
        console.error(error);
      } finally {
        setloading(false);
      }
    };

    fetchdata();
  }, []);

  // links like /?hotels=Goa (footer, cards) open the hotel list of that city
  useEffect(() => {
    const c = router.query.hotels;
    if (!router.isReady || loading || typeof c !== "string" || !c) return;
    const list = hotel.filter((h: any) => (h.location || "").toLowerCase() === c.toLowerCase());
    setbookingtype("hotels");
    setfrom("");
    setto(c);
    setformError("");
    setsuggested(false);
    setsearched(true);
    setsearchresult(list);
    setnotice(list.length ? "" : `No hotels found in ${c}.`);
    router.replace("/", undefined, { shallow: true });
    window.scrollTo({ top: 0, behavior: "smooth" });
  }, [router.isReady, router.query.hotels, loading]);

  const cityOptions = useMemo(() => {
    const cities = new Set<string>();
    flight.forEach((flight) => {
      cities.add(flight.from);
      cities.add(flight.to);
    });
    hotel.forEach((hotel) => {
      cities.add(hotel.location);
    });
    return Array.from(cities).map((city) => ({ value: city, label: city }));
  }, [flight, hotel]);

  // routes that really exist, shown as suggestions when a search finds nothing
  const routeSuggestions = useMemo(() => {
    const seen = new Set<string>();
    const routes: { from: string; to: string }[] = [];
    flight.forEach((f: any) => {
      const key = `${f.from}|${f.to}`;
      if (!seen.has(key)) {
        seen.add(key);
        routes.push({ from: f.from, to: f.to });
      }
    });
    return routes.slice(0, 8);
  }, [flight]);
  if (loading) {
    return <Loader />;
  }
  // helpers for the search
  const dayKey = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  const todayKey = dayKey(new Date());
  const sameCity = (a: string, b: string) => {
    const x = (a || "").trim().toLowerCase();
    const y = (b || "").trim().toLowerCase();
    return !!y && (x === y || x.startsWith(y));
  };
  const upcoming = (f: any) => new Date(f.departureTime).getTime() > Date.now();
  const changeTab = (type: string) => {
    setbookingtype(type);
    setsearchresult([]);
    setsearched(false);
    setformError("");
    setnotice("");
    setsuggested(false);
  };
  const scrollToSearch = () => window.scrollTo({ top: 0, behavior: "smooth" });
  const showHotels = (heading: string, list: any[], city = "") => {
    setbookingtype("hotels");
    setfrom("");
    setto(city);
    setformError("");
    setsuggested(false);
    setsearched(true);
    setsearchresult(list);
    setnotice(list.length ? heading : "No hotels found for this choice.");
    scrollToSearch();
  };
  const showCity = (city: string) => showHotels(`Hotels in ${city}`, hotel.filter((h: any) => sameCity(h.location, city)), city);
  const showBeach = () => showHotels("Hotels near the beach", hotel.filter((h: any) => (h.tags || []).includes("beach")));
  const cityCard = (city: string) => {
    const list = hotel.filter((h: any) => sameCity(h.location, city));
    const withImage = list.find((h: any) => h.images && h.images.length);
    const from = list.length ? Math.min(...list.map((h: any) => h.currentPricePerNight || h.pricePerNight)) : 0;
    return { list, imageUrl: withImage ? withImage.images[0] : "", from };
  };
  const stayText = (n: number, from: number) => `${n} ${n === 1 ? "stay" : "stays"} · from ₹${Math.round(from).toLocaleString("en-IN")}`;
  const featured = [
    {
      title: "Domestic Flights",
      description: "Live demand-based prices, seat selection and a 48-hour price freeze.",
      imageUrl: "https://images.unsplash.com/photo-1436491865332-7a61a109cc05?auto=format&fit=crop&w=800",
      action: "Search flights",
      onClick: () => {
        changeTab("flights");
        scrollToSearch();
      },
    },
    {
      title: "Hotels in Bali",
      description: "Beach resorts and jungle villas, with room selection and a 3D preview.",
      imageUrl: cityCard("Bali").imageUrl || "https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=800",
      action: "View Bali hotels",
      onClick: () => showCity("Bali"),
    },
    {
      title: "Live Flight Status",
      description: "Follow your flights in real time and get delay and gate alerts.",
      imageUrl: "https://images.unsplash.com/photo-1464037866556-6812c9d1c72e?auto=format&fit=crop&w=800",
      action: "Track flights",
      onClick: () => router.push("/flight-status"),
    },
  ];
  const collections = [
    ...["Delhi", "Mumbai", "Bengaluru"].map((city) => {
      const c = cityCard(city);
      return { title: `Stays in & Around ${city}`, imageUrl: c.imageUrl, tag: `${c.list.length} ${c.list.length === 1 ? "STAY" : "STAYS"}`, show: c.list.length > 0, onClick: () => showCity(city) };
    }),
    (() => {
      const beach = hotel.filter((h: any) => (h.tags || []).includes("beach"));
      const img = beach.find((h: any) => h.images && h.images.length);
      return { title: "Beach Destinations", imageUrl: img ? img.images[0] : "", tag: `${beach.length} STAYS`, show: beach.length > 0, onClick: showBeach };
    })(),
  ].filter((c) => c.show);
  const wonders = [
    ["Shimla", "Shimla's Best Kept Secret"],
    ["Udaipur", "Udaipur, the City of Lakes"],
    ["Jaipur", "Jaipur's Pink City Heritage"],
    ["Kolkata", "Kolkata's Old-World Charm"],
  ]
    .map(([city, title]) => {
      const c = cityCard(city);
      return { title, imageUrl: c.imageUrl, subtitle: c.list.length ? stayText(c.list.length, c.from) : "", show: c.list.length > 0, onClick: () => showCity(city) };
    })
    .filter((w) => w.show);
  const handlesearch = () => {
    setformError("");
    setnotice("");
    setsuggested(false);
    setsearchresult([]);
    setsearched(true);
    const fail = (msg: string) => setformError(msg);

    if (bookingtype === "flights") {
      if (!from.trim() || !to.trim()) return fail("Please choose both the From and the To city.");
      if (from.trim().toLowerCase() === to.trim().toLowerCase()) return fail("From and To cities cannot be the same.");
    } else if (!to.trim()) {
      return fail("Please choose a city.");
    }
    // the browser marks half typed or impossible dates (like 31-02) as "bad input" and gives an empty value
    if (dateRef.current?.validity.badInput) return fail("That date is not valid. Please pick a real date from the calendar (for example 28-02-2026).");
    if (date && date < todayKey) return fail("That date is in the past. Please choose today or a later date.");
    if (!travelers || travelers < 1) return fail("Number of travelers must be at least 1.");

    setsearching(true);
    if (user?.id && to) logInteraction(user.id, "SEARCH", undefined, undefined, to);
    if (bookingtype === "flights") {
      const route = flight.filter(
        (FLIGHT) => sameCity(FLIGHT.from, from) && sameCity(FLIGHT.to, to) && upcoming(FLIGHT)
      );
      if (route.length === 0) {
        setnotice(`No flights found from ${from} to ${to}.`);
        setsuggested(true);
      } else if (date) {
        const onDay = route.filter((FLIGHT) => dayKey(new Date(FLIGHT.departureTime)) === date);
        if (onDay.length > 0) {
          setsearchresult(onDay);
        } else {
          setnotice(
            `No flights from ${from} to ${to} on ${new Date(date + "T00:00:00").toLocaleDateString("en-IN", { day: "numeric", month: "long", year: "numeric" })}. Flights on other dates for this route are shown below.`
          );
          setsearchresult(route);
        }
      } else {
        setsearchresult(route);
      }
    } else if (bookingtype === "hotels") {
      const results = hotel.filter((hotel) => sameCity(hotel.location, to));
      if (results.length === 0) {
        setnotice(`No hotels found in ${to}. Hotels are available in: ${Array.from(new Set(hotel.map((h: any) => h.location))).join(", ")}.`);
      }
      setsearchresult(results);
    }
    setsearching(false);
  };
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
  const handlebooknow = (id: any) => {
    if (bookingtype === "flights") {
      router.push(`/book-flight/${id}`);
    } else {
      router.push(`/book-hotel/${id}`);
    }
  };
  return (
    <div
      className="min-h-screen bg-cover bg-center bg-no-repeat"
      style={{
        backgroundImage:
          'url("https://images.unsplash.com/photo-1464037866556-6812c9d1c72e?ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D&auto=format&fit=crop&w=2940&q=80")',
      }}
    >
      <main className="container mx-auto px-4 py-6">
        <nav className="bg-white rounded-xl shadow-lg mx-auto max-w-5xl mb-6 p-4 overflow-x-auto">
          <div className="flex justify-between items-center min-w-max space-x-8">
            <NavItem
              icon={<Plane />}
              text="Flights"
              active={bookingtype === "flights"}
              onClick={() => changeTab("flights")}
            />
            <NavItem
              icon={<Hotel />}
              text="Hotels"
              active={bookingtype === "hotels"}
              onClick={() => changeTab("hotels")}
            />
            <NavItem icon={<HomeIcon />} text="Homestays" onClick={() => comingSoon("Homestays")} />
            <NavItem icon={<Umbrella />} text="Holiday" onClick={() => comingSoon("Holiday")} />
            <NavItem icon={<Train />} text="Trains" onClick={() => comingSoon("Trains")} />
            <NavItem icon={<Bus />} text="Buses" onClick={() => comingSoon("Buses")} />
            <NavItem icon={<Car />} text="Cabs" onClick={() => comingSoon("Cabs")} />
            <NavItem icon={<CreditCard />} text="Forex" onClick={() => comingSoon("Forex")} />
            <NavItem icon={<Shield />} text="Insurance" onClick={() => comingSoon("Insurance")} />
          </div>
        </nav>

        <div className="bg-white rounded-xl shadow-lg mx-auto max-w-5xl p-6">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4">
            {bookingtype === "flights" && (
              <div className="col-span-1">
                <SearchSelect
                  options={cityOptions}
                  placeholder="From"
                  value={from}
                  onChange={setfrom}
                  icon={<MapPin className="text-gray-400" />}
                  subtitle="Enter city or airport"
                />
              </div>
            )}

            <div className="col-span-1">
              <SearchSelect
                options={cityOptions}
                placeholder={bookingtype === "flights" ? "To" : "City"}
                value={to}
                onChange={setto}
                icon={<MapPin className="text-gray-400" />}
                subtitle={
                  bookingtype === "flights"
                    ? "Enter city or airport"
                    : "Enter city"
                }
              />
            </div>

            <div className="col-span-1">
              <SearchInput
                icon={<Calendar className="text-gray-400" />}
                placeholder="Date"
                value={date}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                  setdate(e.target.value)
                }
                inputRef={dateRef}
                subtitle="Select a date (optional)"
                type="date"
                min={todayKey}
              />
            </div>

            <div className="col-span-1">
              <SearchInput
                icon={<Users className="text-gray-400" />}
                placeholder="Travelers"
                value={travelers.toString()}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                  settravelers(parseInt(e.target.value) || 1)
                }
                subtitle="Number of travelers"
                type="number"
                min={1}
                max={9}
              />
            </div>

            <Button className="col-span-1 h-full min-h-[56px]" onClick={handlesearch}>
              SEARCH
            </Button>
          </div>
          <div className="mt-6 min-h-[120px]">
            {formError && (
              <p role="alert" className="mb-4 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                {formError}
              </p>
            )}
            {searched && !formError && (
              <h2 className="text-xl font-semibold mb-3 text-gray-900">
                Search Results
              </h2>
            )}
            {notice && (
              <p className="mb-4 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                {notice}
              </p>
            )}
            {searchresults.length > 0 ? (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {searchresults.map((result) => (
                  <div
                    key={result.id}
                    className="bg-white rounded-lg shadow p-4 border border-gray-200"
                  >
                    {bookingtype === "flights" ? (
                      <>
                        <p className="font-semibold text-lg">
                          Flight Name: {result.flightName}
                        </p>
                        <h3 className="font-semibold text-lg">
                          {result.from} to {result.to}
                        </h3>
                        <p className="text-gray-600">
                          Departure Time: {formatDate(result.departureTime)}
                        </p>
                        <p className="text-gray-600">
                          Arrival Time: {formatDate(result.arrivalTime)}
                        </p>
                        <p className="text-lg font-bold mt-2 flex items-center gap-2 flex-wrap">
                          ₹{Math.round(result.currentPrice || result.price).toLocaleString("en-IN")}
                          {result.priceTrend && result.priceTrend !== "STABLE" && (
                            <span className={`text-xs font-medium ${result.priceTrend === "UP" ? "text-red-600" : "text-green-600"}`}>
                              {result.priceTrend === "UP" ? "▲" : "▼"} {Math.abs(result.priceChangePercent)}%
                            </span>
                          )}
                          <StatusChip status={result.liveStatus} />
                        </p>
                        {result.reviewCount > 0 && (
                          <button
                            className="text-sm text-yellow-600 hover:underline text-left mt-1"
                            onClick={() => router.push(`/book-flight/${result.id}?reviews=1`)}
                          >
                            ★ {result.avgRating.toFixed(1)} ({result.reviewCount} reviews) · Read reviews
                          </button>
                        )}
                        <Button
                          className="w-full mt-4"
                          onClick={() => handlebooknow(result.id)}
                        >
                          Book Now
                        </Button>
                      </>
                    ) : (
                      <>
                        <h3 className="font-semibold text-lg">
                          {result.hotelName}
                        </h3>
                        <p className="text-gray-600">City: {result.location}</p>
                        <p className="text-lg font-bold mt-2 flex items-center gap-2 flex-wrap">
                          ₹{Math.round(result.currentPricePerNight || result.pricePerNight).toLocaleString("en-IN")} per night
                          {result.priceTrend && result.priceTrend !== "STABLE" && (
                            <span className={`text-xs font-medium ${result.priceTrend === "UP" ? "text-red-600" : "text-green-600"}`}>
                              {result.priceTrend === "UP" ? "▲" : "▼"} {Math.abs(result.priceChangePercent)}%
                            </span>
                          )}
                        </p>
                        {result.reviewCount > 0 && (
                          <button
                            className="text-sm text-yellow-600 hover:underline text-left"
                            onClick={() => router.push(`/book-hotel/${result.id}?reviews=1`)}
                          >
                            ★ {result.avgRating.toFixed(1)} ({result.reviewCount} reviews) · Read reviews
                          </button>
                        )}
                        <Button
                          className="w-full mt-4"
                          onClick={() => handlebooknow(result.id)}
                        >
                          Book Now
                        </Button>
                      </>
                    )}
                  </div>
                ))}
              </div>
            ) : searched && !formError && suggested && bookingtype === "flights" ? (
              <div>
                <p className="text-sm text-gray-600 mb-2">Routes that are currently available (tap one to search it):</p>
                <div className="flex flex-wrap gap-2">
                  {routeSuggestions.map((r) => (
                    <button
                      key={`${r.from}-${r.to}`}
                      className="rounded-full border border-blue-200 bg-blue-50 px-3 py-1 text-sm text-blue-700 hover:bg-blue-100"
                      onClick={() => {
                        setfrom(r.from);
                        setto(r.to);
                        setnotice("");
                        setsuggested(false);
                        setsearched(false);
                      }}
                    >
                      {r.from} → {r.to}
                    </button>
                  ))}
                </div>
              </div>
            ) : !searched ? (
              <p className="text-gray-600">
                {bookingtype === "flights"
                  ? "Choose where you are flying from and to, then press SEARCH."
                  : "Choose a city and press SEARCH to see the hotels there."}
              </p>
            ) : null}
          </div>
        </div>
        <div className="max-w-7xl mx-auto px-4">
          {/* Holidays and peak periods that raise the prices */}
          <PriceAlerts />

          {/* Personalised recommendations */}
          <Recommendations />

          {/* Offers Section */}
          <section className="my-16">
            <h2 className="text-2xl font-bold mb-8 text-white">Explore MakeMyTour</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
              {featured.map((offer) => (
                <OfferCard key={offer.title} {...offer} />
              ))}
            </div>
          </section>

          {/* Collections Section */}
          {collections.length > 0 && (
          <section className="my-16">
            <div className="flex justify-between items-center mb-8">
              <h2 className="text-2xl font-bold text-white">
                Handpicked Collections for You
              </h2>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
              {collections.map((collection) => (
                <CollectionCard key={collection.title} {...collection} />
              ))}
            </div>
          </section>
          )}

          {/* Wonders Section */}
          {wonders.length > 0 && (
          <section className="my-16">
            <div className="flex justify-between items-center mb-8">
              <h2 className="text-2xl font-bold text-white">
                Unlock Lesser-Known <span></span> Wonders of India
              </h2>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
              {wonders.map((wonder) => (
                <WonderCard key={wonder.title} {...wonder} />
              ))}
            </div>
          </section>
          )}
        </div>
      </main>
    </div>
  );
}
const FALLBACK_IMAGE = "https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=800";

const OfferCard = ({ title, description, imageUrl, action, onClick }: any) => {
  return (
    <div className="bg-white rounded-lg shadow-md overflow-hidden">
      <img src={imageUrl || FALLBACK_IMAGE} alt={title} className="w-full h-48 object-cover" />
      <div className="p-4">
        <h3 className="font-semibold text-lg mb-2">{title}</h3>
        <p className="text-gray-600 text-sm">{description}</p>
        <button onClick={onClick} className="mt-4 px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors">
          {action}
        </button>
      </div>
    </div>
  );
};

const CollectionCard = ({ title, imageUrl, tag, onClick }: any) => {
  return (
    <button onClick={onClick} className="relative group cursor-pointer overflow-hidden rounded-lg text-left" aria-label={`${title}: see the hotels`}>
      <img
        src={imageUrl || FALLBACK_IMAGE}
        alt={title}
        className="w-full h-64 object-cover transition-transform duration-300 group-hover:scale-110"
      />
      <div className="absolute inset-0 bg-gradient-to-b from-transparent to-black/70">
        <div className="absolute top-4 left-4">
          <span className="bg-white text-black text-sm font-semibold px-2 py-1 rounded">{tag}</span>
        </div>
        <div className="absolute bottom-4 left-4 right-4">
          <h3 className="text-white text-lg font-semibold">{title}</h3>
        </div>
      </div>
    </button>
  );
};

const WonderCard = ({ title, imageUrl, subtitle, onClick }: any) => {
  return (
    <button onClick={onClick} className="relative group cursor-pointer overflow-hidden rounded-lg text-left" aria-label={`${title}: see the hotels`}>
      <img
        src={imageUrl || FALLBACK_IMAGE}
        alt={title}
        className="w-full h-64 object-cover transition-transform duration-300 group-hover:scale-110"
      />
      <div className="absolute inset-0 bg-gradient-to-b from-transparent to-black/70">
        <div className="absolute bottom-4 left-4 right-4">
          <h3 className="text-white text-lg font-semibold">{title}</h3>
          {subtitle && <p className="text-white/90 text-sm mt-0.5">{subtitle}</p>}
        </div>
      </div>
    </button>
  );
};
function NavItem({ icon, text, active = false, onClick }: any) {
  return (
    <button
      className={`flex flex-col items-center p-2 rounded-lg transition-colors ${
        active ? "text-blue-500" : "text-gray-600 hover:text-blue-500"
      }`}
      onClick={onClick}
    >
      {icon}
      <span className="text-sm mt-1 whitespace-nowrap">{text}</span>
    </button>
  );
}

function SearchInput({
  icon,
  placeholder,
  value,
  onChange,
  subtitle,
  type = "text",
  min,
  max,
  inputRef,
}: any) {
  return (
    <div className="border rounded-lg p-3 hover:border-blue-500 cursor-pointer h-full">
      <div className="flex items-center space-x-2">
        {icon}
        <div className="flex-1 min-w-0">
          <div className="text-sm text-gray-500 truncate">{placeholder}</div>
          <input
            ref={inputRef}
            type={type}
            min={min}
            max={max}
            value={value}
            onChange={onChange}
            className="font-semibold w-full bg-transparent outline-none"
            placeholder={placeholder}
          />
          <div className="text-xs text-gray-400 truncate">{subtitle}</div>
        </div>
      </div>
    </div>
  );
}
