import { useCallback, useEffect, useState } from "react";
import dynamic from "next/dynamic";
import { useSelector } from "react-redux";
import { BedDouble, Box, Check, Crown, Heart, Images, Maximize2, Users } from "lucide-react";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { errorMessage, getPreferences, getRooms, savePreferences } from "@/api/extra";
import { useEvents } from "@/hooks/useEvents";
import { toast } from "@/lib/toast";
import { inr } from "@/lib/format";

const Room3DPreview = dynamic(() => import("./Room3DPreview"), {
  ssr: false,
  loading: () => <div className="h-[280px] rounded-lg bg-gray-100 animate-pulse" />,
});

type Props = {
  hotelId: string;
  stayDate?: string;
  selected: string;
  onSelect: (room: any) => void;
};

/** Room-type grid with live prices, upgrade pricing, photos, a 3D preview and a saved room preference. */
const RoomPicker = ({ hotelId, stayDate, selected, onSelect }: Props) => {
  const user = useSelector((state: any) => state.user.user);
  const [data, setData] = useState<any>(null);
  const [error, setError] = useState("");
  const [preferred, setPreferred] = useState("");
  const [preview, setPreview] = useState<any>(null);
  const [mode, setMode] = useState<"3d" | "photos">("3d");
  const [photo, setPhoto] = useState(0);
  const [spin, setSpin] = useState(true);
  const [initial, setInitial] = useState(true);

  const load = useCallback(async () => {
    try {
      const d = await getRooms(hotelId, stayDate, user?.id);
      setData(d);
      setError("");
    } catch (e) {
      setError(errorMessage(e));
    }
  }, [hotelId, stayDate, user?.id]);

  useEffect(() => {
    load();
  }, [load]);
  useEffect(() => {
    if (!user?.id) return;
    getPreferences(user.id).then((p) => setPreferred(p?.roomType || "")).catch(() => null);
  }, [user?.id]);

  // real-time availability and price updates
  useEvents(["ROOM_UPDATE", "PRICE_UPDATE"], (e) => {
    if (Array.isArray(e) ? e.some((c: any) => c.type === "HOTEL" && c.id === hotelId) : e?.hotelId === hotelId) load();
  });

  // first time: select the room the user prefers (or the first one)
  useEffect(() => {
    if (!data || !initial) return;
    const wanted = data.roomTypes.find((r: any) => r.name === preferred && r.availableRooms > 0);
    const first = data.roomTypes.find((r: any) => r.availableRooms > 0);
    const pick = wanted || first;
    if (pick) {
      onSelect(pick);
      setInitial(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data, preferred]);

  // keep the selected room in sync with new prices
  useEffect(() => {
    if (!data || !selected) return;
    const current = data.roomTypes.find((r: any) => r.name === selected);
    if (current) onSelect(current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data]);

  const savePreferred = async (name: string) => {
    if (!user?.id) {
      toast({ title: "Log in to save your room preference", tone: "warning" });
      return;
    }
    try {
      await savePreferences(user.id, { roomType: name });
      setPreferred(name);
      toast({ title: `${name} saved as your preferred room`, tone: "success" });
    } catch (e) {
      toast({ title: "Could not save", message: errorMessage(e), tone: "error" });
    }
  };

  if (error) return <p className="text-sm text-red-600">{error}</p>;
  if (!data) return <div className="h-64 rounded-xl bg-gray-100 animate-pulse" />;

  return (
    <div>
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
        {data.roomTypes.map((room: any) => {
          const active = selected === room.name;
          const soldOut = room.availableRooms <= 0;
          return (
            <div
              key={room.name}
              className={`rounded-xl border-2 overflow-hidden bg-white transition-shadow flex flex-col ${
                active ? "border-blue-500 shadow-lg" : "border-gray-200 hover:shadow-md"
              } ${soldOut ? "opacity-60" : ""}`}
            >
              <div className="relative">
                <img src={room.images[0]} alt={`${room.name} room`} className="h-36 w-full object-cover" loading="lazy" />
                {room.premium && (
                  <span className="absolute top-2 left-2 bg-amber-400 text-amber-950 text-xs font-semibold px-2 py-0.5 rounded-full inline-flex items-center gap-1">
                    <Crown className="w-3 h-3" /> Premium
                  </span>
                )}
                {preferred === room.name && (
                  <span className="absolute top-2 right-2 bg-pink-600 text-white text-xs px-2 py-0.5 rounded-full inline-flex items-center gap-1">
                    <Heart className="w-3 h-3" /> Your pick
                  </span>
                )}
              </div>
              <div className="p-3 flex flex-col gap-2 flex-1">
                <div className="flex items-start justify-between gap-2">
                  <h4 className="font-semibold text-lg">{room.name}</h4>
                  <div className="text-right">
                    <div className="font-bold">{inr(room.pricePerNight)}</div>
                    <div className="text-[11px] text-gray-500">per night</div>
                  </div>
                </div>
                <p className="text-sm text-gray-600">{room.description}</p>
                <div className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-gray-600">
                  <span className="inline-flex items-center gap-1">
                    <BedDouble className="w-3.5 h-3.5" />
                    {room.bedType}
                  </span>
                  <span className="inline-flex items-center gap-1">
                    <Maximize2 className="w-3.5 h-3.5" />
                    {room.sizeSqFt} sq ft
                  </span>
                  <span className="inline-flex items-center gap-1">
                    <Users className="w-3.5 h-3.5" />
                    {room.capacity} guests
                  </span>
                </div>
                <ul className="text-xs text-gray-600 grid grid-cols-2 gap-x-2 gap-y-0.5">
                  {room.features.map((f: string) => (
                    <li key={f} className="flex items-start gap-1">
                      <Check className="w-3 h-3 mt-0.5 text-green-600 shrink-0" />
                      {f}
                    </li>
                  ))}
                </ul>
                <div className="text-xs">
                  {room.upgradeCost > 0 ? (
                    <span className="text-amber-700 font-medium">Upgrade: +{inr(room.upgradeCost)} per night</span>
                  ) : (
                    <span className="text-green-700 font-medium">Base room rate</span>
                  )}
                  <span className={`ml-2 ${room.availableRooms <= 3 ? "text-red-600 font-medium" : "text-gray-500"}`}>
                    {soldOut ? "Sold out" : `${room.availableRooms} left`}
                  </span>
                </div>
                <div className="mt-auto flex flex-wrap gap-2 pt-1">
                  <button
                    type="button"
                    disabled={soldOut}
                    onClick={() => onSelect(room)}
                    className={`flex-1 min-w-[96px] text-sm px-3 py-1.5 rounded-md ${
                      active ? "bg-blue-600 text-white" : "border border-blue-600 text-blue-600 hover:bg-blue-50"
                    } disabled:opacity-50`}
                  >
                    {active ? "Selected" : "Select"}
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setPreview(room);
                      setMode("3d");
                      setPhoto(0);
                    }}
                    className="text-sm px-3 py-1.5 rounded-md border hover:bg-gray-50 inline-flex items-center gap-1"
                  >
                    <Box className="w-4 h-4" /> 3D / Photos
                  </button>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <Dialog open={!!preview} onOpenChange={(o) => !o && setPreview(null)}>
        <DialogContent className="sm:max-w-[760px] bg-white max-h-[92vh] overflow-y-auto">
          {preview && (
            <>
              <DialogHeader>
                <DialogTitle className="flex items-center gap-2">
                  {preview.name} room · {inr(preview.pricePerNight)} / night
                </DialogTitle>
              </DialogHeader>
              <div className="inline-flex rounded-md border overflow-hidden text-sm w-fit">
                <button className={`px-3 py-1.5 inline-flex items-center gap-1 ${mode === "3d" ? "bg-gray-900 text-white" : "bg-white"}`} onClick={() => setMode("3d")}>
                  <Box className="w-4 h-4" /> 3D view
                </button>
                <button className={`px-3 py-1.5 inline-flex items-center gap-1 ${mode === "photos" ? "bg-gray-900 text-white" : "bg-white"}`} onClick={() => setMode("photos")}>
                  <Images className="w-4 h-4" /> Photos
                </button>
              </div>
              {mode === "3d" ? (
                <div>
                  <div className="h-[300px] sm:h-[380px]">
                    <Room3DPreview roomType={preview.name} autoRotate={spin} />
                  </div>
                  <div className="flex items-center justify-between mt-2 text-xs text-gray-500">
                    <span>Drag to look around · scroll or pinch to zoom</span>
                    <button className="underline" onClick={() => setSpin(!spin)}>
                      {spin ? "Stop turning" : "Turn automatically"}
                    </button>
                  </div>
                </div>
              ) : (
                <div>
                  <img src={preview.images[photo]} alt={`${preview.name} room photo ${photo + 1}`} className="w-full h-[280px] sm:h-[380px] object-cover rounded-lg" />
                  <div className="flex gap-2 mt-2">
                    {preview.images.map((src: string, i: number) => (
                      <button key={src} onClick={() => setPhoto(i)} className={`rounded-md overflow-hidden border-2 ${photo === i ? "border-blue-500" : "border-transparent"}`}>
                        <img src={src} alt="" className="h-14 w-20 object-cover" />
                      </button>
                    ))}
                  </div>
                </div>
              )}
              <p className="text-sm text-gray-600">
                {preview.description} {preview.bedType} · {preview.sizeSqFt} sq ft · up to {preview.capacity} guests.
              </p>
              <div className="flex flex-wrap gap-2">
                <button
                  className="px-4 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700"
                  onClick={() => {
                    onSelect(preview);
                    setPreview(null);
                  }}
                >
                  Select this room
                </button>
                <button className="px-4 py-2 rounded-md border text-sm inline-flex items-center gap-1 hover:bg-gray-50" onClick={() => savePreferred(preview.name)}>
                  <Heart className="w-4 h-4" /> Save as my preferred room
                </button>
              </div>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default RoomPicker;
