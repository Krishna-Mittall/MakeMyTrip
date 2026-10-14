# Run MakeMyTour on your computer

You need: **Java 17+**, **Node.js 18+**, and a **MongoDB** database (either local MongoDB Community, or a free MongoDB Atlas cluster).

## 1. Database
* **Local MongoDB**: install MongoDB Community Server and start it. Nothing else to do (default `mongodb://localhost:27017`).
* **Atlas (free)**: create a cluster, add a database user, allow your IP (or 0.0.0.0/0), copy the connection string and use it as `MONGODB_URI` below.

## 2. Backend (port 8080)
Open a terminal in this folder.

Windows (PowerShell)
```
$env:MONGODB_URI="mongodb://localhost:27017"     # or your Atlas string
.\mvnw.cmd spring-boot:run
```
Mac / Linux
```
MONGODB_URI="mongodb://localhost:27017" ./mvnw spring-boot:run
```
First start downloads dependencies (a few minutes). On an empty database it creates demo flights/hotels and two accounts.

## 3. Frontend (port 3000)
New terminal:
```
cd makemytour
npm install
npm run dev
```
Open http://localhost:3000

(If the backend is not on `http://localhost:8080`, set `NEXT_PUBLIC_API_URL` before `npm run dev`.)

## 4. Logins
| Role | Email | Password |
|------|-------|----------|
| Admin | admin@makemytour.com | Admin@123 |
| Moderator | moderator@makemytour.com | Mod@12345 |
| Traveller | sign up from the site | any |

## 5. What to try
1. **Seat & room selection**: open any flight (Book Now) -> seat map; open a hotel -> pick a room, "3D / Photos".
2. **Dynamic pricing**: "Price insights" on flight/hotel pages (history chart, "Freeze this price").
3. **Book** a flight/hotel as a traveller, then open **Profile**.
4. **Cancellation & refunds**: Profile -> "Cancel booking" (pick reason, partial seats) -> refund tracker.
5. **Reviews**: flight/hotel page -> "Write a review" (stars, text, photos); "Report" a review, then log in as moderator -> MODERATE tab -> Keep/Remove.
6. **Live flight status**: top bar "Flight Status"; track a flight (bell icon); admin can simulate a delay on a flight's status page.
7. **Push notifications**: bell icon -> "Turn on push notifications" (allow it in the browser). Track a flight, then as admin simulate a delay on its status page.
8. **Recommendations**: home page "Recommended for you" (use "Why this?", Helpful / Not relevant). Browse and book a few things first to see it personalise.
