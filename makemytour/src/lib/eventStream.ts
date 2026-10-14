// One Server-Sent-Events connection to the backend, shared by all components.
// Live seat maps, prices, flight status and notifications arrive through it.
import { eventsUrl } from "@/api/extra";

type Handler = (data: any) => void;
type Stream = { source: EventSource; handlers: Map<string, Set<Handler>>; count: number };
const streams = new Map<string, Stream>();

export function subscribeEvents(userId: string | undefined, types: string[], handler: Handler) {
  if (typeof window === "undefined" || typeof EventSource === "undefined") {
    return () => {};
  }
  const key = userId || "anonymous";
  let stream = streams.get(key);
  if (!stream) {
    stream = { source: new EventSource(eventsUrl(userId)), handlers: new Map(), count: 0 };
    streams.set(key, stream);
  }
  const current = stream;
  current.count++;
  types.forEach((type) => {
    let set = current.handlers.get(type);
    if (!set) {
      set = new Set();
      current.handlers.set(type, set);
      const listeners = set;
      current.source.addEventListener(type, (event) => {
        let data: any = null;
        try {
          data = JSON.parse((event as MessageEvent).data);
        } catch (e) {
          return;
        }
        listeners.forEach((h) => h(data));
      });
    }
    set.add(handler);
  });
  return () => {
    types.forEach((type) => current.handlers.get(type)?.delete(handler));
    current.count--;
    if (current.count <= 0) {
      current.source.close();
      streams.delete(key);
    }
  };
}
