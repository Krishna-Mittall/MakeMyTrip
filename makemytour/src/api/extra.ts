// API calls of the newer features: seats & rooms, live prices, cancellation & refunds, reviews,
// flight status, notifications and recommendations. The first part of the app uses ./index.js.
import axios from "axios";
import { BACKEND_URL } from "@/api";

const api = axios.create({ baseURL: BACKEND_URL });
const get = (url: string, params?: any) => api.get(url, { params }).then((r) => r.data);
const post = (url: string, params?: any, body?: any) =>
  api.post(url, body ?? null, { params }).then((r) => r.data);
const del = (url: string, params?: any) => api.delete(url, { params }).then((r) => r.data);

/** The readable message of an error that came back from the backend. */
export const errorMessage = (e: any): string =>
  e?.response?.data?.message || (e?.response ? "Something went wrong" : "Cannot reach the server. Please try again.");

export const eventsUrl = (userId?: string) =>
  `${BACKEND_URL}/events${userId ? `?userId=${encodeURIComponent(userId)}` : ""}`;
export const photoSrc = (path: string) => (path?.startsWith("http") ? path : `${BACKEND_URL}${path}`);

// ---- seats, rooms, saved preferences
export const getSeatMap = (flightId: string, userId?: string) => get(`/seats/flight/${flightId}`, { userId });
export const holdSeats = (flightId: string, userId: string, seats: string[]) =>
  post(`/seats/hold`, { flightId, userId, seats: seats.join(",") });
export const releaseSeats = (flightId: string, userId: string) => post(`/seats/release`, { flightId, userId });
export const getRooms = (hotelId: string, stayDate?: string, userId?: string) =>
  get(`/rooms/hotel/${hotelId}`, { stayDate, userId });
export const getPreferences = (userId: string) => get(`/preferences/${userId}`);
export const savePreferences = (userId: string, prefs: any) =>
  api.put(`/preferences/${userId}`, prefs).then((r) => r.data);

// ---- dynamic pricing
export const getFlightQuote = (id: string, userId?: string) => get(`/pricing/flight/${id}`, { userId });
export const getHotelQuote = (id: string, stayDate?: string, userId?: string) =>
  get(`/pricing/hotel/${id}`, { stayDate, userId });
export const getPriceHistory = (type: string, id: string, days = 30) => get(`/pricing/history/${type}/${id}`, { days });
export const createFreeze = (userId: string, type: string, itemId: string, hours: number, stayDate?: string) =>
  post(`/pricing/freeze`, { userId, type, itemId, hours, stayDate });
export const getFreezes = (userId: string) => get(`/pricing/freezes`, { userId });
export const cancelFreeze = (id: string, userId: string) => del(`/pricing/freeze/${id}`, { userId });
export const getSurges = () => get(`/pricing/surges`);
export const getHotelCalendar = (hotelId: string, days = 14) => get(`/pricing/hotel/${hotelId}/calendar`, { days });

// ---- cancellation and refunds
export const getCancelReasons = () => get(`/cancellation/reasons`);
export const getRefundPolicy = () => get(`/cancellation/policy`);
export const previewCancel = (userId: string, reference: string, seats?: string[], quantity?: number) =>
  get(`/cancellation/preview`, { userId, reference, seats: seats?.length ? seats.join(",") : undefined, quantity });
export const cancelBooking = (
  userId: string,
  reference: string,
  reason: string,
  seats?: string[],
  quantity?: number,
  comments?: string
) =>
  post(`/cancellation/cancel`, {
    userId,
    reference,
    reason,
    seats: seats?.length ? seats.join(",") : undefined,
    quantity,
    comments: comments || undefined,
  });
export const getRefunds = (userId: string) => get(`/cancellation/refunds`, { userId });
export const adminRefunds = (adminId: string, status?: string) => get(`/admin/refunds`, { adminId, status });
export const adminRefundAnalytics = (adminId: string) => get(`/admin/refunds/analytics`, { adminId });
export const adminAdvanceRefund = (id: string, adminId: string) => post(`/admin/refunds/${id}/advance`, { adminId });

// ---- reviews
export const getReviews = (params: any) => get(`/reviews`, params);
export const getFlagReasons = () => get(`/reviews/flag-reasons`);
export const saveReview = (body: any) => post(`/reviews`, undefined, body);
export const deleteReview = (id: string, userId: string) => del(`/reviews/${id}`, { userId });
export const toggleHelpful = (id: string, userId: string) => post(`/reviews/${id}/helpful`, { userId });
export const replyToReview = (id: string, userId: string, text: string) =>
  post(`/reviews/${id}/reply`, { userId }, { text });
export const flagReview = (id: string, userId: string, reason: string, details?: string) =>
  post(`/reviews/${id}/flag`, { userId, reason, details: details || undefined });
export const uploadReviewPhoto = (userId: string, file: Blob) => {
  const form = new FormData();
  form.append("file", file, "photo.jpg");
  return api.post(`/reviews/photos`, form, { params: { userId } }).then((r) => r.data);
};
export const modQueue = (userId: string, status = "PENDING") => get(`/admin/moderation/queue`, { userId, status });
export const modStats = (userId: string) => get(`/admin/moderation/stats`, { userId });
export const modDismiss = (id: string, userId: string, note?: string) =>
  post(`/admin/moderation/${id}/dismiss`, { userId, note: note || undefined });
export const modRemove = (id: string, userId: string, note?: string) =>
  post(`/admin/moderation/${id}/remove`, { userId, note: note || undefined });

// ---- notifications
export const getNotifications = (userId: string, limit = 30) => get(`/notifications`, { userId, limit });
export const markNotificationRead = (id: string, userId: string) => post(`/notifications/${id}/read`, { userId });
export const markAllNotificationsRead = (userId: string) => post(`/notifications/read-all`, { userId });

// ---- live flight status
export const getFlightBoard = (hours = 24, q?: string, userId?: string) =>
  get(`/flight-status/board`, { hours, q: q || undefined, userId });
export const getTrackedFlights = (userId: string) => get(`/flight-status/tracked`, { userId });
export const getFlightStatus = (flightId: string, userId?: string) => get(`/flight-status/${flightId}`, { userId });
export const getFlightStatusBatch = (ids: string[], userId?: string) =>
  ids.length ? get(`/flight-status/batch`, { ids: ids.join(","), userId }) : Promise.resolve([]);
export const trackFlight = (userId: string, flightId: string) => post(`/flight-status/track`, { userId, flightId });
export const untrackFlight = (userId: string, flightId: string) => del(`/flight-status/track`, { userId, flightId });
export const demoAdvanceFlight = (flightId: string, userId: string) => post(`/flight-status/${flightId}/demo`, { userId });
export const simulateFlight = (flightId: string, adminId: string, action: string, minutes?: number, reason?: string) =>
  post(`/mock-api/flights/${flightId}/simulate`, { adminId, action, minutes, reason: reason || undefined });

// ---- recommendations
export const getRecommendations = (userId?: string, limit = 6, type?: string) =>
  get(`/recommendations`, { userId, limit, type });
export const sendRecommendationFeedback = (userId: string, itemType: string, itemId: string, value: string) =>
  post(`/recommendations/feedback`, { userId, itemType, itemId, value });
export const logInteraction = (userId: string, type: string, itemType?: string, itemId?: string, query?: string) =>
  post(`/recommendations/interaction`, { userId, type, itemType, itemId, query }).catch(() => null);

// ---- browser push notifications
export const getPushKey = () => get(`/push/key`);
export const savePushSubscription = (userId: string, subscription: any) =>
  api.post(`/push/subscribe`, subscription, { params: { userId } }).then((r) => r.data);
export const removePushSubscription = (userId: string, endpoint: string) => del(`/push/subscribe`, { userId, endpoint });
export const sendPushTest = (userId: string) => post(`/push/test`, { userId });
