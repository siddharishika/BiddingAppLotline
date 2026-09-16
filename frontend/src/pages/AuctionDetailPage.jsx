import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { getAuction, placeBid } from "../api";
import { useAuth } from "../auth";
import Timer from "../components/Timer";
import LotCard from "../components/LotCard";
import { bidderLabel, displayLot, formatWhen, inferredWinner, lotShelf, money, nextMinimum, paymentDueLabel, pillClass, priceCaption, statusLabel } from "../format";
import { useToast } from "../toast";

export default function AuctionDetailPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const toast = useToast();
  const [lot, setLot] = useState(null);
  const [amount, setAmount] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getAuction(id)
      .then((data) => {
        if (cancelled) {
          return;
        }
        setLot(data);
        setAmount(nextMinimum(data.currentPrice, data.minIncrement).toFixed(2));
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err.message);
          toast.error(err.message);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  useEffect(() => {
    if (!lot) {
      return undefined;
    }
    const client = new Client({
      webSocketFactory: () => new SockJS("/ws"),
      debug: () => {},
      onConnect: () => {
        client.subscribe(`/topic/auctions/${id}`, (message) => {
          const payload = JSON.parse(message.body);
          setLot((current) => {
            if (!current) {
              return current;
            }
            const currentPrice = payload.currentPrice ?? current.currentPrice;
            const increment = payload.minIncrement ?? current.minIncrement;
            const minBid = currentPrice != null && increment != null
              ? nextMinimum(currentPrice, increment)
              : payload.nextMinimum ?? current.minBid;
            if (minBid != null) {
              setAmount(Number(minBid).toFixed(2));
            }
            const mine = user && payload.bidderUsername === user.username;
            return {
              ...current,
              currentPrice,
              minIncrement: increment,
              minBid,
              bidderCount: payload.bidCount ?? current.bidderCount,
              status: payload.status ?? current.status,
              winnerUsername: payload.status === "SOLD"
                ? payload.bidderUsername ?? current.winnerUsername
                : current.winnerUsername,
              myLastBidAmount: mine
                ? (payload.currentPrice ?? current.myLastBidAmount)
                : current.myLastBidAmount,
            };
          });
        });
      },
    });
    client.activate();
    return () => {
      client.deactivate();
    };
  }, [id, lot?.id, user?.username]);

  async function onBid(event) {
    event.preventDefault();
    const bid = Number(amount);
    const minimum = nextMinimum(lot.currentPrice, lot.minIncrement);
    if (Number.isNaN(bid) || bid < minimum) {
      toast.error(`Bid must be at least ${money(minimum)}`);
      return;
    }
    setBusy(true);
    try {
      await placeBid(id, amount);
      toast.success("Bid placed.");
      const refreshed = await getAuction(id);
      setLot(refreshed);
      setAmount(nextMinimum(refreshed.currentPrice, refreshed.minIncrement).toFixed(2));
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  if (!lot && !error) {
    return <main className="wrap page-status"><p className="muted">Loading lot…</p></main>;
  }
  if (!lot) {
    return <main className="wrap page-status"><div className="flash flash-err">{error}</div></main>;
  }

  const view = displayLot(lot);
  const live = view.status === "LIVE";
  const upcoming = view.status === "SCHEDULED";
  const sold = view.status === "SOLD";
  const ended = view.status === "ENDED";
  const withdrawn = view.status === "CANCELLED";
  const closed = sold || ended || withdrawn;
  const winner = inferredWinner(view, user?.username);
  const isWinner = Boolean(lot.winner) || winner === "You";
  const minimum = nextMinimum(lot.currentPrice, lot.minIncrement);
  const catalogueTo = upcoming ? "/auctions/upcoming" : live ? "/auctions/live" : "/auctions";
  const catalogueLabel = upcoming ? "Upcoming" : live ? "Live" : "Lot catalogue";
  const detailPriceCaption = upcoming
    ? "Starting price"
    : live
      ? "Current highest bid"
      : priceCaption(view.status);

  return (
    <main className="wrap section">
      <p className="crumb">
        {lot.collectionId ? (
          <>
            <Link to="/collections">Collection catalogue</Link>
            {" / "}
            <Link to={`/collections/${lot.collectionId}`}>{lot.collectionName}</Link>
            {" / "}
            {lot.title}
          </>
        ) : (
          <>
            <Link to={catalogueTo}>{catalogueLabel}</Link> / Lot {lot.id}
          </>
        )}
      </p>

      <div className="detail-grid">
        <div
          className="detail-media"
          style={lot.imageUrl ? { backgroundImage: `url('${lot.imageUrl}')` } : undefined}
        />
        <aside className="bid-panel">
          <div className="lot-top">
            <span className="muted">
              {lot.collectionId ? (
                <Link className="text-link" to={`/collections/${lot.collectionId}`}>{lotShelf(lot)}</Link>
              ) : lotShelf(lot)}
            </span>
            <span className={pillClass(view.status)}>{statusLabel(view.status)}</span>
          </div>
          <h1>{lot.title}</h1>
          <p className="seller">Offered by <strong>{lot.sellerUsername}</strong></p>
          <div className="price-block">
            <span className="muted">{detailPriceCaption}</span>
            <p className="price">{money(upcoming ? lot.startingPrice : lot.currentPrice)}</p>
            {user && !lot.seller && lot.myLastBidAmount != null && (
              <p className="my-last-bid">Your previous bid <strong>{money(lot.myLastBidAmount)}</strong></p>
            )}
            {sold && (
              <p className="winner-line">Won by <strong>{winner || "a bidder"}</strong></p>
            )}
            <p className="bidder-count">
              {upcoming ? `Increment ${money(lot.minIncrement)}` : bidderLabel(lot.bidderCount)}
            </p>
          </div>
          {!closed && (
            <div className="timer-block">
              <span className="muted">{upcoming ? "Opens in" : "Closes in"}</span>
              <p className={`timer${upcoming ? " timer-opens" : ""}`}>
                {upcoming
                  ? <Timer endTime={lot.startTime} endedLabel="Opening" />
                  : <Timer endTime={lot.endTime} endedLabel="00:00:00" />}
              </p>
              {upcoming && (
                <p className="hint">
                  Bidding starts {formatWhen(lot.startTime)} and runs until {formatWhen(lot.endTime)}.
                </p>
              )}
            </div>
          )}

          {live && !user && <Link className="btn btn-gold btn-full" to="/login">Sign in to bid</Link>}
          {live && user && lot.seller && (
            <p className="hint">You listed this lot, so bidding is closed to you.</p>
          )}
          {live && user && !lot.seller && (
            <form className="stack" onSubmit={onBid}>
              <label>
                Your bid (minimum {money(minimum)})
                <input
                  type="number"
                  step="0.01"
                  min={minimum}
                  value={amount}
                  onChange={(event) => setAmount(event.target.value)}
                  required
                />
              </label>
              <button className="btn btn-gold btn-full" type="submit" disabled={busy}>
                {busy ? "Placing…" : "Place bid"}
              </button>
            </form>
          )}

          {sold && (
            <>
              {isWinner && !lot.paid && lot.paymentDueAt && (
                <p className="hint">{paymentDueLabel(lot)} · due {formatWhen(lot.paymentDueAt)}</p>
              )}
              {isWinner && !lot.paid && (
                <Link className="btn btn-gold btn-full" to={`/auctions/${lot.id}/pay`}>Settle payment</Link>
              )}
              {isWinner && lot.paid && <p className="hint">This lot is paid.</p>}
              {lot.seller && !lot.paid && lot.paymentDueAt && (
                <p className="hint">Winner has until {formatWhen(lot.paymentDueAt)} to settle. After that the lot returns as unsold.</p>
              )}
            </>
          )}
          {ended && <p>This lot closed without a settled sale. The issuer can reopen it from My lots.</p>}
          {withdrawn && (
            <p>This lot was withdrawn from the floor. The issuer can reopen it from My lots or My collections.</p>
          )}
          {upcoming && (
            <p>
              This lot is catalogued, but the room is not open yet. Watch the clock — bidding begins{" "}
              <strong>{formatWhen(lot.startTime)}</strong>.
            </p>
          )}
        </aside>
      </div>

      <section className="lot-copy">
        <h2>The lot</h2>
        <p className="body-copy">{lot.description}</p>
        <dl className="meta-list">
          <div><dt>Starting price</dt><dd>{money(lot.startingPrice)}</dd></div>
          <div><dt>Increment</dt><dd>{money(lot.minIncrement)}</dd></div>
          <div><dt>Opens</dt><dd>{formatWhen(lot.startTime)}</dd></div>
          <div><dt>Closes</dt><dd>{formatWhen(lot.endTime)}</dd></div>
        </dl>
      </section>

      {lot.collectionName && lot.alsoInCollection?.length > 0 && (
        <section className="also-in">
          <h2>Also in {lot.collectionName}</h2>
          <p className="muted">
            Other lots from this collection.{" "}
            <Link className="text-link" to={`/collections/${lot.collectionId}`}>Open collection →</Link>
          </p>
          <div className="lot-grid">
            {lot.alsoInCollection.map((sibling) => <LotCard key={sibling.id} lot={sibling} />)}
          </div>
        </section>
      )}
    </main>
  );
}
