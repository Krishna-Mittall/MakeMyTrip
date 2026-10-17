This is a [Next.js](https://nextjs.org) project bootstrapped with [`create-next-app`](https://nextjs.org/docs/pages/api-reference/create-next-app).

## Getting Started

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

You can start editing the page by modifying `pages/index.tsx`. The page auto-updates as you edit the file.

[API routes](https://nextjs.org/docs/pages/building-your-application/routing/api-routes) can be accessed on [http://localhost:3000/api/hello](http://localhost:3000/api/hello). This endpoint can be edited in `pages/api/hello.ts`.

The `pages/api` directory is mapped to `/api/*`. Files in this directory are treated as [API routes](https://nextjs.org/docs/pages/building-your-application/routing/api-routes) instead of React pages.

This project uses [`next/font`](https://nextjs.org/docs/pages/building-your-application/optimizing/fonts) to automatically optimize and load [Geist](https://vercel.com/font), a new font family for Vercel.

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn-pages-router) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/pages/building-your-application/deploying) for more details.

---

# MakeMyTour – internship feature set

Six features were added on top of the existing Spring Boot + MongoDB + Next.js project (existing files were only touched where needed; almost everything is in new files).

| # | Feature | Backend (new) | Frontend (new) |
|---|---------|---------------|----------------|
| 1 | Cancellation & refunds | `CancellationService`, `RefundPolicy`, `AdminRefundController` | `components/cancellation/*`, admin "Refunds" tab |
| 2 | Reviews & ratings | `ReviewService`, `ReviewController`, `AdminModerationController` | `components/reviews/*`, admin "Moderation" tab |
| 3 | Live flight status | `FlightStatusService`, `MockFlightApiService` (`/mock-api/flights`) | `pages/flight-status`, `components/flight/*`, notification bell |
| 4 | Seat & room selection | `SeatService`, `RoomService` | `components/booking/SeatMap`, `RoomPicker`, `Room3DPreview` |
| 5 | Dynamic pricing | `PricingService`, `PricingRules` | `components/booking/PriceInsight`, `PriceChart` |
| 6 | Recommendations (hotels, flights, destinations) | `RecommendationService` (content + collaborative filtering + popularity) | `components/recommendations/Recommendations` |

Live updates (seat map, prices, flight status, notifications) are pushed with Server-Sent Events (`GET /events?userId=`). Browser push notifications use Web Push (service worker `makemytour/public/sw.js`, VAPID keys are generated once and stored in MongoDB, or set `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY`); they work over https or on localhost.

## Running locally
```bash
# backend (needs MongoDB)
MONGODB_URI=mongodb://localhost:27017/makemytrip ./mvnw spring-boot:run
# frontend
cd makemytour && npm install && NEXT_PUBLIC_API_URL=http://localhost:8080 npm run dev
```
An empty database is filled with demo flights/hotels. Demo accounts: `admin@makemytour.com / Admin@123` (ADMIN), `moderator@makemytour.com / Mod@12345` (MODERATOR).
Tests: `./mvnw test -Dtest='RefundPolicyTest,PricingRulesTest,RecommendationMathTest'`.

## Notes
* Refund policy: cancelled within 24 hours of the reservation (booking time) -> 50 % of the booking amount. After that the time left decides: flights 90 % (72h+ before departure), 75 % (24-72h), 25 % (3-24h), 0 % (<3h); hotels 100 % (72h+), 75 % (24-72h), 25 % (<24h). Partial cancellation (single seats/rooms) is supported. In the demo the refund tracker moves PENDING -> PROCESSED -> COMPLETED within minutes; real timelines are shown as "expected" text.
* Flight data comes from a built-in mock flight API; price history before the app was first run is generated for the demo.
