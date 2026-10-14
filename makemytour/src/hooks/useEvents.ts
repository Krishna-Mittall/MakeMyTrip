import { useEffect, useRef } from "react";
import { useSelector } from "react-redux";
import { subscribeEvents } from "@/lib/eventStream";

/** Calls `handler` whenever the backend sends one of the given live events (SEAT_UPDATE, PRICE_UPDATE ...). */
export function useEvents(types: string[], handler: (data: any, type?: string) => void) {
  const user = useSelector((state: any) => state.user.user);
  const ref = useRef(handler);
  ref.current = handler;
  const key = types.join(",");
  useEffect(() => {
    const unsubscribers = types.map((type) => subscribeEvents(user?.id, [type], (data) => ref.current(data, type)));
    return () => unsubscribers.forEach((u) => u());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.id, key]);
}
