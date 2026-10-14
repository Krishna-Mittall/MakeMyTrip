import { useEffect, useState } from "react";
import { useRouter } from "next/router";
import { CheckCircle2, Info, AlertTriangle, XCircle, X } from "lucide-react";
import { onToast, ToastItem } from "@/lib/toast";

const tones = {
  info: { icon: Info, color: "text-blue-600", border: "border-blue-200" },
  success: { icon: CheckCircle2, color: "text-green-600", border: "border-green-200" },
  warning: { icon: AlertTriangle, color: "text-amber-600", border: "border-amber-200" },
  error: { icon: XCircle, color: "text-red-600", border: "border-red-200" },
};

const ToastHost = () => {
  const [items, setItems] = useState<ToastItem[]>([]);
  const router = useRouter();
  useEffect(
    () =>
      onToast((item) => {
        setItems((list) => [...list.slice(-3), item]);
        setTimeout(() => setItems((list) => list.filter((i) => i.id !== item.id)), 7000);
      }),
    []
  );
  return (
    <div className="fixed bottom-4 right-4 left-4 sm:left-auto z-[100] flex flex-col gap-2 sm:w-96 pointer-events-none">
      {items.map((t) => {
        const tone = tones[t.tone || "info"];
        const Icon = tone.icon;
        return (
          <div
            key={t.id}
            role="status"
            className={`pointer-events-auto bg-white border ${tone.border} rounded-xl shadow-lg p-3 flex gap-3 ${t.href ? "cursor-pointer" : ""}`}
            onClick={() => t.href && router.push(t.href)}
          >
            <Icon className={`w-5 h-5 mt-0.5 shrink-0 ${tone.color}`} />
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold text-gray-900">{t.title}</p>
              {t.message && <p className="text-sm text-gray-600 break-words">{t.message}</p>}
            </div>
            <button
              aria-label="Close"
              className="text-gray-400 hover:text-gray-600"
              onClick={(e) => {
                e.stopPropagation();
                setItems((list) => list.filter((i) => i.id !== t.id));
              }}
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        );
      })}
    </div>
  );
};

export default ToastHost;
