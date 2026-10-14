import { useEffect, useState } from "react";
import { duration } from "@/lib/format";

/**
 * Live "departs in" / "lands in" text. It counts down by itself every second and is based on the clock of the
 * flight sent by the server, so a fast-forwarded demo flight also shows the right ETA.
 */
const EtaCountdown = ({ status }: { status: any }) => {
  const [now, setNow] = useState(Date.now());
  const [offset, setOffset] = useState(0);
  useEffect(() => {
    if (status?.serverTime) setOffset(new Date(status.serverTime).getTime() - Date.now());
  }, [status?.serverTime, status?.updatedAt]);
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);
  if (!status) return null;
  const t = now + offset;
  const toDep = Math.round((new Date(status.estimatedDeparture).getTime() - t) / 1000);
  const toArr = Math.round((new Date(status.estimatedArrival).getTime() - t) / 1000);
  const fmt = (s: number) => {
    const m = Math.floor(s / 60);
    const sec = s % 60;
    return m >= 120 ? duration(m) : `${m}m ${String(sec).padStart(2, "0")}s`;
  };
  switch (status.status) {
    case "CANCELLED":
      return <span className="text-red-600">Flight cancelled</span>;
    case "LANDED":
      return <span className="text-gray-600">Landed</span>;
    case "IN_AIR":
    case "DEPARTED":
      return <span className="text-sky-700">ETA: lands in {fmt(Math.max(toArr, 0))}</span>;
    case "BOARDING":
      return <span className="text-blue-700">Departs in {fmt(Math.max(toDep, 0))}</span>;
    default:
      return <span>Departs in {toDep > 0 ? fmt(toDep) : "moments"}</span>;
  }
};

export default EtaCountdown;
