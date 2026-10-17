import React from "react";
import Link from "next/link";

const linkClass = "hover:text-white hover:underline";

/** Footer: a short description of the site and links that really lead somewhere. */
const Footer = () => {
  return (
    <footer className="bg-black text-gray-300 pt-12 pb-8">
      <div className="max-w-7xl mx-auto px-4">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8 mb-10">
          <div className="md:col-span-1">
            <h3 className="text-xl font-bold mb-4 text-white">MakeMyTour</h3>
            <p className="text-sm leading-relaxed">
              Book flights and hotels with live prices, interactive seat and room selection, flight status alerts, easy cancellation with a refund
              tracker, and recommendations made for you.
            </p>
          </div>
          <div>
            <h4 className="font-semibold mb-3 text-white">EXPLORE</h4>
            <ul className="text-sm space-y-2">
              <li>
                <Link href="/" className={linkClass}>
                  Search flights and hotels
                </Link>
              </li>
              <li>
                <Link href="/flight-status" className={linkClass}>
                  Live flight status
                </Link>
              </li>
              <li>
                <Link href="/profile" className={linkClass}>
                  My dashboard
                </Link>
              </li>
            </ul>
          </div>
          <div>
            <h4 className="font-semibold mb-3 text-white">POPULAR HOTELS</h4>
            <ul className="text-sm space-y-2">
              {["Goa", "Delhi", "Mumbai", "Bali"].map((city) => (
                <li key={city}>
                  <Link href={`/?hotels=${city}`} className={linkClass}>
                    Hotels in {city}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
          <div>
            <h4 className="font-semibold mb-3 text-white">YOUR BOOKINGS</h4>
            <ul className="text-sm space-y-2">
              <li>
                <Link href="/profile" className={linkClass}>
                  Cancel a booking
                </Link>
              </li>
              <li>
                <Link href="/profile" className={linkClass}>
                  Refund tracker
                </Link>
              </li>
              <li>
                <Link href="/profile" className={linkClass}>
                  My price freezes
                </Link>
              </li>
            </ul>
          </div>
        </div>

        <div className="border-t border-gray-700 pt-6 text-sm text-center md:text-left">© 2026 MakeMyTour. All rights reserved.</div>
      </div>
    </footer>
  );
};

export default Footer;
