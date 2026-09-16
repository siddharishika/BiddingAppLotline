import { Link } from "react-router-dom";
import { useAuth } from "../auth";
import Timer from "./Timer";
import { bidderLabel, formatWhen, lotShelf, money, pillClass, priceCaption, statusLabel, displayLot, inferredWinner } from "../format";

export default function LotCard({ lot }) {
  const { user } = useAuth();
  const view = displayLot(lot);
  const upcoming = view.status === "SCHEDULED";
  const live = view.status === "LIVE";
  const sold = view.status === "SOLD";
  const winner = inferredWinner(view, user?.username);

  return (
    <Link className="lot-card" to={`/auctions/${lot.id}`}>
      <div
        className="lot-image"
        style={lot.imageUrl ? { backgroundImage: `url('${lot.imageUrl}')` } : undefined}
      />
      <div className="lot-body">
        <div className="lot-top">
          <span className="muted">{lotShelf(lot)}</span>
          <span className={pillClass(view.status)}>{statusLabel(view.status)}</span>
        </div>
        <h3>{view.title}</h3>
        {view.description && <p className="lot-blurb">{view.description}</p>}
        {upcoming ? (
          <p className="bidder-count">Opens {formatWhen(view.startTime)}</p>
        ) : (
          <p className="bidder-count">{bidderLabel(view.bidderCount)}</p>
        )}
        {view.myLastBidAmount != null && (
          <p className="my-last-bid">Your last bid <strong>{money(view.myLastBidAmount)}</strong></p>
        )}
        {sold && (
          <p className="winner-line">Won by <strong>{winner || "a bidder"}</strong></p>
        )}
        <div className="lot-bottom">
          <span>{priceCaption(view.status)} <strong>{money(view.currentPrice)}</strong></span>
          {upcoming ? (
            <Timer endTime={view.startTime} endedLabel="Opening" />
          ) : live ? (
            <Timer endTime={view.endTime} endedLabel="00:00:00" />
          ) : null}
        </div>
      </div>
    </Link>
  );
}
