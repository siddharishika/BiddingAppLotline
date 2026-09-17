# Lotline — Virtual Auction House

**Lotline** is a full-stack online auction platform: consignors list lots and multi-lot collections, bidders compete on a live floor with real-time price updates, and winners settle through a payment gateway within a fixed window. Business rules live on the server and in the database—not in local storage or hardcoded UI shortcuts.

Built as a portfolio / interview project to demonstrate end-to-end product thinking: domain modeling, concurrent bidding, WebSockets, security, payment persistence, scheduled settlement, and a React consignor desk.

---

## Problem it solves

Traditional “toy auction” demos stop at place-a-bid. Lotline covers the full auction lifecycle that a real house needs:

1. **Catalogue** — live and upcoming lots, grouped into collections when a sale has many items.
2. **Live floor** — concurrent bidders, increment rules, seller exclusion, and a broadcast tape.
3. **Hammer** — a scheduler closes lots when the clock expires and assigns (or does not assign) a winner.
4. **Settlement** — winners pay within 7 days; unpaid lots return as unsold; payment attempts remain as receipts.
5. **Consignor control** — withdraw, reopen, and delete based on auction **status**, not special-cased titles or users.

---



## Tech stack


| Layer       | Choice                                                    | Why it matters in conversation                            |
| ----------- | --------------------------------------------------------- | --------------------------------------------------------- |
| Frontend    | React 18, Vite, React Router                              | SPA with auth-gated routes; Vite proxies `/api` and `/ws` |
| Backend     | Spring Boot 3.4, Java 17                                  | Layered REST + services; transactional checkout           |
| Persistence | Spring Data JPA + PostgreSQL on [Aiven](https://aiven.io) | Shared cloud catalogue for demos and production           |
| Realtime    | Spring WebSocket, STOMP, SockJS                           | Bid events on `/topic/auctions/{id}` without polling      |
| Security    | Spring Security, BCrypt, session cookie, CSRF             | Public browse; auth for bid, consign, pay                 |
| Payments    | Pluggable `PaymentGateway` (Stripe-style)                 | Success/decline paths; PAN never stored                   |
| Tests       | JUnit 5, Mockito, MockMvc                                 | Bid rules, close/forfeit, payments, security              |


---



## Architecture

```
Browser (React)
    │  REST  /api/**          WebSocket /ws (STOMP)
    ▼
Spring Boot
    ├── Controllers (auth, auctions, collections, account/payments)
    ├── Services (Bid, Auction, Collection, Payment)
    ├── Scheduler (close expired lots; forfeit unpaid after 7 days)
    ├── EventPublisher → STOMP topics
    └── JPA repositories → Aiven PostgreSQL
```

**Separation of concerns**

- Controllers map HTTP and auth context; they do not encode bid or payment rules.
- Services own invariants (increment, seller cannot bid, payment window, issuer-only withdraw).
- The UI reads catalogue, receipts, and awaiting lots from the API. Transient navigation state is not treated as source of truth for payments.

---



## Domain model


| Entity            | Role                                                                                           |
| ----------------- | ---------------------------------------------------------------------------------------------- |
| **User**          | Bidder / consignor / admin; BCrypt password; username on the public tape                       |
| **LotCollection** | Named sale grouping one or more lots (issuer owns the collection)                              |
| **AuctionItem**   | A lot: price, schedule, status, optional winner, link to collection                            |
| **Bid**           | Immutable paddle entry (bidder, amount, timestamp)                                             |
| **Payment**       | Charge attempt: amount, status (`SUCCEEDED` / `FAILED`), last four, gateway id, failure reason |




### Lot status lifecycle

```
SCHEDULED ──► LIVE ──► SOLD (highest bidder) or ENDED (no bids / unpaid forfeit)
                │
                └── CANCELLED (withdrawn by issuer while SCHEDULED or ENDED)
```


| Status      | Meaning                                                     |
| ----------- | ----------------------------------------------------------- |
| `SCHEDULED` | Not yet open; can be withdrawn                              |
| `LIVE`      | Accepting bids; countdown to `endTime`                      |
| `SOLD`      | Winner assigned; payment due within 7 days of hammer        |
| `ENDED`     | Closed unsold (no bids, or unpaid forfeit); can be reopened |
| `CANCELLED` | Withdrawn; can be reopened or permanently deleted           |


Consignor actions are **status-driven** so demo data and production listings share one code path.

---



## Features



### Live bidding

- Minimum bid = current price + lot increment; server rejects underbids.
- Sellers cannot bid on their own lots (`Forbidden` / bid-rejected path).
- Each accepted bid updates `currentPrice`, is persisted, and is published to subscribers so open detail pages refresh without a full reload.
- Lot and collection cards show countdown timers; expiry is authoritative on the server via `AuctionScheduler`.



### Collections

- Multi-lot sales share branding and issuer ownership.
- Live collections surface **N lots live out of M** from server-computed counts—not a client-side guess.
- Collection withdraw / reopen / delete applies the same status rules across member lots.



### Consignor desk (`My lots` / `My collections`)


| Action   | Allowed when           | Effect                                              |
| -------- | ---------------------- | --------------------------------------------------- |
| Withdraw | `SCHEDULED` or `ENDED` | → `CANCELLED`                                       |
| Reopen   | `CANCELLED` or `ENDED` | Fresh live window; price reset to starting price    |
| Delete   | `CANCELLED` only       | Removes lot (and empty collection when appropriate) |




### Payments and settlement

- Only the winning bidder may checkout; already-paid and closed-window cases fail with clear errors.
- **Failed** gateway responses are persisted in a `REQUIRES_NEW` transaction so a rolled-back checkout still leaves a receipt row.
- **Receipts** and **Awaiting payment** come from `GET /api/account/payments` (payment history + unpaid `SOLD` wins).
- After **7 days** unpaid, the scheduler forfeits the lot to `ENDED`, clears the winner and bids for reopen, and **keeps** payment rows for audit/receipts.
- Card PAN is sent to the gateway only; DB stores last four digits and a `ch_…` id on success.



### Account views

- **My bids** — suggested live lots, lots you bid on, wins, and losses.
- **Payments** — full charge history plus outstanding settlement list with Pay / Retry.



### Security and privacy

- Session-based auth with CSRF cookie for mutating requests.
- Anonymous catalogue browse; register/login for bid, list, and pay.
- Public floor shows usernames, never emails.
- Credentials for cloud Postgres live in gitignored `config/application-aiven.properties`.

---



## Design decisions worth discussing

1. **Server is source of truth** — timers and “who won” are not trusted from the client; the scheduler closes lots and assigns winners.
2. **Status machines over hardcoding** — withdraw/reopen/delete and payment eligibility key off status and role, so seeded “Quiet Study” style lots behave like any other listing.
3. **Durable failed payments** — declined cards still create `Payment` rows so the consignor/bidder UX and audit trail stay honest.
4. **Forfeit without erasing history** — unpaid settlement returns inventory to the issuer without deleting receipt data.
5. **Gateway abstraction** — `PaymentGateway` keeps Stripe-like charge semantics swappable for a real provider later.
6. **Cloud Postgres as source of truth** — demo and runtime data live on Aiven; reseeding is intentional and scripted (`scripts/seed-demo.sh`).

---



## Demo walkthrough 


| Account  | Password      | Suggested story                                                 |
| -------- | ------------- | --------------------------------------------------------------- |
| `mara`   | `password123` | Bid, win, open **Payments** (receipts + awaiting), consign desk |
| `julian` | `password123` | Second browser — compete on the same live lot                   |
| `seller` | `password123` | List lots / collections                                         |
| `admin`  | `password123` | Admin role present for extension                                |


**Talk track:** open two browsers as `mara` and `julian` → bid on a live lot → watch WebSocket price update → wait for or explain scheduler close → settle with test card `4242424242424242` (decline: `4000000000000002`) → show receipt in Payments → show consignor withdraw on an upcoming collection by status.

---



## Repository layout

```
BiddingApp/
├── frontend/                 React + Vite UI
├── src/main/java/            API, domain, services, WebSocket, security
├── scripts/seed-demo.sh      Wipe + reseed Aiven demo catalogue (occasional)
├── scripts/show-db.py        Inspect Aiven tables (optional --wipe)
├── scripts/migrate-db.py     Apply schema SQL on Aiven if needed
├── config/                   Aiven credentials (gitignored)
├── Dockerfile / render.yaml  Production deploy on Render
└── docker-compose.yml        Optional local Postgres 16 (dev only)
```

---



## Run

**Prerequisites:** JDK 17, Maven, Node.js, an Aiven PostgreSQL service that is **Running**, and `config/application-aiven.properties` (gitignored) with JDBC URL, username, and password (`sslmode=require`).

**Seed / refresh the cloud demo catalogue** (wipes Aiven demo tables, then reseeds):

```bash
./scripts/seed-demo.sh
```

**Inspect Aiven:**

```bash
python3 -B scripts/show-db.py
```

(Use a venv with `psycopg2-binary` if needed; `seed-demo.sh` creates `scripts/.venv` once.)

**Start the app** (API uses the `aiven` profile by default via Maven):

```bash
mvn spring-boot:run
cd frontend && npm install && npm run dev
```

UI: [http://localhost:5173](http://localhost:5173) · API: [http://localhost:8080/api](http://localhost:8080/api)

If the Aiven service is powered off or unreachable, start it in the [Aiven console](https://console.aiven.io) and confirm Allowed IPs include your machine (or `0.0.0.0/0` for demos).

Optional: `docker compose up -d` for a throwaway local Postgres — the default path is Aiven.