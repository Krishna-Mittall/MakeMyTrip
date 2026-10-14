// Very small toast system: toast({...}) shows a message in the corner of the screen.
export type ToastItem = { id: number; title: string; message?: string; tone?: "info" | "success" | "error" | "warning"; href?: string };
type Listener = (item: ToastItem) => void;
const listeners = new Set<Listener>();
let counter = 0;

export function toast(t: Omit<ToastItem, "id">) {
  const item = { ...t, id: ++counter };
  listeners.forEach((l) => l(item));
}
export function onToast(listener: Listener) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
