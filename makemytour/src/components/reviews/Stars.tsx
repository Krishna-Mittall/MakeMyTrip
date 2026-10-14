import { Star } from "lucide-react";

export const StarRow = ({ value, size = 16 }: { value: number; size?: number }) => (
  <span className="inline-flex" aria-label={`${value.toFixed(1)} out of 5 stars`}>
    {[1, 2, 3, 4, 5].map((i) => {
      const fill = Math.max(0, Math.min(1, value - (i - 1)));
      return (
        <span key={i} className="relative inline-block" style={{ width: size, height: size }}>
          <Star width={size} height={size} className="absolute inset-0 text-gray-300" />
          <span className="absolute inset-0 overflow-hidden" style={{ width: `${fill * 100}%` }}>
            <Star width={size} height={size} className="text-yellow-400 fill-yellow-400" />
          </span>
        </span>
      );
    })}
  </span>
);

export const StarInput = ({ value, onChange }: { value: number; onChange: (v: number) => void }) => (
  <div className="inline-flex gap-1" role="radiogroup" aria-label="Your rating">
    {[1, 2, 3, 4, 5].map((i) => (
      <button
        key={i}
        type="button"
        role="radio"
        aria-checked={value === i}
        aria-label={`${i} star${i > 1 ? "s" : ""}`}
        onClick={() => onChange(i)}
        className="p-0.5"
      >
        <Star className={`w-7 h-7 ${i <= value ? "text-yellow-400 fill-yellow-400" : "text-gray-300"}`} />
      </button>
    ))}
  </div>
);
