import { useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { Armchair, BedDouble, Heart } from "lucide-react";
import { errorMessage, getPreferences, savePreferences } from "@/api/extra";
import { toast } from "@/lib/toast";

/** The saved seat and room preferences of the user (they are suggested again on every new booking). */
const PreferencesCard = () => {
  const user = useSelector((state: any) => state.user.user);
  const [prefs, setPrefs] = useState<any>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (user?.id) getPreferences(user.id).then(setPrefs).catch(() => setPrefs({ seatPosition: "ANY", seatTier: "ANY", roomType: "" }));
  }, [user?.id]);

  if (!user || !prefs) return null;

  const save = async () => {
    setSaving(true);
    try {
      setPrefs(await savePreferences(user.id, { seatPosition: prefs.seatPosition || "ANY", seatTier: prefs.seatTier || "ANY", roomType: prefs.roomType || "" }));
      toast({ title: "Preferences saved", message: "We will suggest them on your next booking.", tone: "success" });
    } catch (e) {
      toast({ title: "Could not save", message: errorMessage(e), tone: "error" });
    } finally {
      setSaving(false);
    }
  };

  const field = "block mt-1 w-full border rounded-md px-2 py-1.5 text-sm bg-white";
  return (
    <div className="bg-white rounded-xl shadow-lg p-4 sm:p-6">
      <h2 className="text-2xl font-bold flex items-center gap-2 mb-1">
        <Heart className="w-6 h-6 text-pink-600" /> My travel preferences
      </h2>
      <p className="text-sm text-gray-600 mb-4">Saved seat and room choices. They are suggested again when you book a flight or a hotel.</p>
      <div className="grid sm:grid-cols-3 gap-4">
        <label className="text-sm text-gray-700">
          <span className="inline-flex items-center gap-1"><Armchair className="w-4 h-4" /> Seat position</span>
          <select className={field} value={prefs.seatPosition || "ANY"} onChange={(e) => setPrefs({ ...prefs, seatPosition: e.target.value })}>
            <option value="ANY">No preference</option>
            <option value="WINDOW">Window</option>
            <option value="MIDDLE">Middle</option>
            <option value="AISLE">Aisle</option>
          </select>
        </label>
        <label className="text-sm text-gray-700">
          <span className="inline-flex items-center gap-1"><Armchair className="w-4 h-4" /> Seat type</span>
          <select className={field} value={prefs.seatTier || "ANY"} onChange={(e) => setPrefs({ ...prefs, seatTier: e.target.value })}>
            <option value="ANY">No preference</option>
            <option value="STANDARD">Standard</option>
            <option value="EXTRA_LEGROOM">Extra legroom</option>
            <option value="PREMIUM">Premium</option>
          </select>
        </label>
        <label className="text-sm text-gray-700">
          <span className="inline-flex items-center gap-1"><BedDouble className="w-4 h-4" /> Room type</span>
          <select className={field} value={prefs.roomType || ""} onChange={(e) => setPrefs({ ...prefs, roomType: e.target.value })}>
            <option value="">No preference</option>
            <option value="Standard">Standard</option>
            <option value="Deluxe">Deluxe</option>
            <option value="Suite">Suite</option>
          </select>
        </label>
      </div>
      <button onClick={save} disabled={saving} className="mt-4 px-4 py-2 rounded-lg bg-pink-600 text-white text-sm hover:bg-pink-700 disabled:opacity-60">
        {saving ? "Saving..." : "Save preferences"}
      </button>
    </div>
  );
};

export default PreferencesCard;
