# Lotline — Virtual Auction House

Full-stack online auction house: consignors list lots and collections, bidders compete on a live floor with WebSocket price updates, and winners settle through Stripe within a fixed window. Business rules live on the server and in PostgreSQL—not in local storage or UI shortcuts.

Portfolio project covering domain modeling, concurrent bidding, WebSockets, session security, payment persistence, scheduled settlement, and a React consignor desk.

## Problem

Toy auction demos stop at place-a-bid. Lotline covers the full house lifecycle:

1. **Catalogue** — live and upcoming lots, grouped into collections.
2. **Live floor** — concurrent bidders, increment rules, seller exclusion, broadcast tape.
3. **Hammer** — scheduler closes lots at expiry and assigns (or does not assign) a winner.
4. **Settlement** — winners pay within 7 days; unpaid lots return unsold; payment attempts stay as receipts.
5. **Consignor control** — withdraw, reopen, and delete by auction **status**, not hardcoded titles or users.

## Tech stack

| Layer | Choice | Why |
| --- | --- | --- |
| Frontend | React 18, Vite, React Router | SPA with auth-gated routes; Vite proxies `/api` and `/ws` |
| Backend | Spring Boot 3.4, Java 17 | Layered REST + services; transactional checkout |
| Persistence | Spring Data JPA + PostgreSQL on [Aiven](https://aiven.io) | Shared cloud catalogue for demos and production |
| Realtime | Spring WebSocket, STOMP, SockJS | Bid events on `/topic/auctions/{id}` |
| Security | Spring Security, BCrypt, session cookie, CSRF | Public browse; auth for bid, consign, pay |
| Payments | Stripe Checkout via `PaymentGateway` | Cards stay on Stripe; receipts persist in Postgres |
| Tests | JUnit 5, Mockito, MockMvc | Bid rules, close/forfeit, payments, security |

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

Controllers map HTTP and auth; they do not encode bid or payment rules. Services own invariants (increment, seller cannot bid, payment window, issuer-only withdraw). The UI reads catalogue, receipts, and awaiting lots from the API.

## Domain model

| Entity | Role |
| --- | --- |
| **User** | Bidder / consignor / admin; BCrypt password; username on the public tape |
| **LotCollection** | Named sale grouping lots (issuer owns the collection) |
| **AuctionItem** | A lot: price, schedule, status, optional winner, collection link |
| **Bid** | Immutable paddle entry (bidder, amount, timestamp) |
| **Payment** | Charge attempt: amount, status (`SUCCEEDED` / `FAILED`), last four, gateway id, failure reason |

### Lot status lifecycle

```
SCHEDULED ──► LIVE ──► SOLD (highest bidder) or ENDED (no bids / unpaid forfeit)
                │
                └── CANCELLED (withdrawn by issuer while SCHEDULED or ENDED)
```

| Status | Meaning |
| --- | --- |
| `SCHEDULED` | Not yet open; can be withdrawn |
| `LIVE` | Accepting bids; countdown to `endTime` |
| `SOLD` | Winner assigned; payment due within 7 days of hammer |
| `ENDED` | Closed unsold (no bids, or unpaid forfeit); can be reopened |
| `CANCELLED` | Withdrawn; can be reopened or permanently deleted |

Consignor actions are status-driven so demo data and production listings share one code path.

## Features

**Live bidding** — minimum bid is current price + increment (server rejects underbids). Sellers cannot bid on their own lots. Accepted bids persist, update `currentPrice`, and publish to subscribers. Countdown timers are display-only; `AuctionScheduler` owns expiry.

**Collections** — multi-lot sales share branding and issuer. Live collections show **N lots live out of M** from server-computed counts. Withdraw / reopen / delete apply the same status rules to member lots.

**Consignor desk** (`My lots` / `My collections`)

| Action | Allowed when | Effect |
| --- | --- | --- |
| Withdraw | `SCHEDULED` or `ENDED` | → `CANCELLED` |
| Reopen | `CANCELLED` or `ENDED` | Fresh live window; price reset to starting price |
| Delete | `CANCELLED` only | Removes lot (and empty collection when appropriate) |

**Payments** — winners pay on Stripe Checkout; expired or failed sessions are stored as receipts. Receipts and awaiting lots come from `GET /api/account/payments`. After 7 days unpaid, the scheduler forfeits the lot to `ENDED`, clears winner and bids for reopen, and keeps payment rows. Card PAN never reaches Lotline.

**Account** — **My bids** (suggested live lots, bids, wins, losses). **Payments** (charge history + outstanding settlement with Pay / Retry).

**Security** — session auth with CSRF for mutating requests. Anonymous catalogue; login for bid, list, and pay. Public floor shows usernames, never emails. Aiven credentials live in gitignored `config/application-aiven.properties`.

## Design decisions

1. **Server is source of truth** — the scheduler closes lots and assigns winners; the client is not trusted for timers or hammer.
2. **Status machines over hardcoding** — withdraw/reopen/delete and payment eligibility key off status and role.
3. **Durable failed payments** — expired or failed Stripe sessions still create `Payment` rows.
4. **Forfeit without erasing history** — unpaid settlement returns inventory without deleting receipts.
5. **Gateway abstraction** — `PaymentGateway` is Stripe Checkout in production and an in-memory hosted session in tests.
6. **Cloud Postgres** — demo and runtime data live on Aiven; reseed with `scripts/seed-db.py --seed`.

## Demo

| Account | Password | Suggested story |
| --- | --- | --- |
| `mara` | `password123` | Bid, win, open **Payments**, consign desk |
| `julian` | `password123` | Second browser — compete on the same live lot |
| `seller` | `password123` | List lots / collections |
| `admin` | `password123` | Admin role for extension |

Two browsers as `mara` and `julian` → bid on a live lot → WebSocket price update → scheduler close → Stripe Checkout (`4242424242424242`; decline `4000000000000002`) → receipt in Payments → consignor withdraw on an upcoming collection by status.

## Layout

```
BiddingApp/
├── frontend/                 React + Vite UI
├── src/main/java/            API, domain, services, WebSocket, security
├── scripts/seed-db.py        Inspect, wipe, or reseed Aiven
├── scripts/schema.sql        Idempotent Postgres schema
├── config/                   Aiven credentials (gitignored)
├── Dockerfile / render.yaml  Production deploy on Render
```
