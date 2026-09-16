import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listAuctions } from "../api";
import LotCard from "../components/LotCard";
import Timer from "../components/Timer";
import { bidderLabel, money, pillClass, statusLabel } from "../format";
import { useToast } from "../toast";

export default function HomePage() {
  const toast = useToast();
  const [liveLots, setLiveLots] = useState([]);
  const [upcomingLots, setUpcomingLots] = useState([]);

  useEffect(() => {
    Promise.all([listAuctions("LIVE"), listAuctions("SCHEDULED")])
      .then(([live, upcoming]) => {
        setLiveLots(live);
        setUpcomingLots(upcoming);
      })
      .catch((err) => toast.error(err.message));
  }, [toast]);

  return (
    <main>
      <section className="hero">
        <div className="wrap hero-grid">
          <div>
            <p className="eyebrow">Internet auction · live paddle</p>
            <h1>List a lot. Watch the room. Bid before the hammer.</h1>
            <p className="lede">
              Lotline is a private saleroom for watches, books, art, and objects. Authorized accounts only.
              The current bid and clock update as they happen.
            </p>
            <div className="hero-actions">
              <Link className="btn btn-gold" to="/auctions/live">View live lots</Link>
              <Link className="btn btn-ink" to="/auctions">Lot catalogue</Link>
              <Link className="btn btn-ink" to="/collections">Collection catalogue</Link>
            </div>
          </div>
          <aside className="hero-panel">
            <p className="panel-kicker">On the block now</p>
            {liveLots.length === 0 && <p>No live lots. List something to open the room.</p>}
            {liveLots.slice(0, 3).map((lot) => (
              <article className="hero-lot" key={lot.id}>
                <div>
                  <span className={pillClass(lot.status)}>{statusLabel(lot.status)}</span>
                  <h3>{lot.title}</h3>
                </div>
                <div className="hero-lot-meta">
                  <strong>{money(lot.currentPrice)}</strong>
                  <span className="bidder-count">{bidderLabel(lot.bidderCount)}</span>
                  <Timer endTime={lot.endTime} />
                </div>
              </article>
            ))}
          </aside>
        </div>
      </section>

      <section className="wrap section">
        <div className="section-head">
          <h2>Live lots</h2>
          <Link className="text-link" to="/auctions/live">All live lots →</Link>
        </div>
        {liveLots.length === 0 && <p className="muted">Nothing on the block right now.</p>}
        <div className="lot-grid">
          {liveLots.map((lot) => <LotCard key={lot.id} lot={lot} />)}
        </div>
      </section>

      <section className="wrap section" style={{ paddingTop: 0 }}>
        <div className="section-head">
          <h2>Upcoming lots</h2>
          <Link className="text-link" to="/auctions/upcoming">All upcoming lots →</Link>
        </div>
        {upcomingLots.length === 0 && (
          <p className="muted">No scheduled sales. List a lot with a future opening time to preview it here.</p>
        )}
        <div className="lot-grid">
          {upcomingLots.map((lot) => <LotCard key={lot.id} lot={lot} />)}
        </div>
      </section>
    </main>
  );
}
