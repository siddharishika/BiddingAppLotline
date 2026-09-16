import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getMyBids } from "../api";
import CollectionCard from "../components/CollectionCard";
import LotCard from "../components/LotCard";
import { inferredWinner, money, paymentDueLabel } from "../format";
import { useAuth } from "../auth";
import { useToast } from "../toast";

const emptyBids = {
  suggestions: [],
  suggestedCollections: [],
  placed: [],
  placedCollections: [],
  wins: [],
  losses: [],
};

export default function MyBidsPage() {
  const toast = useToast();
  const { user } = useAuth();
  const [data, setData] = useState(emptyBids);

  useEffect(() => {
    getMyBids()
      .then((payload) => setData({ ...emptyBids, ...payload }))
      .catch((err) => toast.error(err.message));
  }, [toast]);

  return (
    <main className="wrap section">
      <p className="eyebrow">Paddle board</p>
      <h1>My bids</h1>
      <p className="lede">
        Three suggested lots and collections, then every lot you have bid on and every collection
        that includes those lots.
      </p>

      <section>
        <h2>Suggested bids</h2>
        <p className="muted">Three live rooms that fit on one row. A starting point, not a full catalogue.</p>
        {data.suggestions.length === 0 && <p>No live lots to suggest right now.</p>}
        <div className="lot-grid three-up">
          {data.suggestions.map((lot) => <LotCard key={lot.id} lot={lot} />)}
        </div>
      </section>

      <section className="spaced">
        <h2>Suggested collections</h2>
        <p className="muted">Live collections with more than one lot that you have not bid on yet.</p>
        {data.suggestedCollections.length === 0 && <p>No live collections to suggest right now.</p>}
        <div className="lot-grid three-up">
          {data.suggestedCollections.map((collection) => (
            <CollectionCard key={collection.id} collection={collection} />
          ))}
        </div>
      </section>

      <section className="spaced">
        <h2>Lots you bid on</h2>
        {data.placed.length === 0 && <p>You have not placed a bid yet.</p>}
        {data.placed.length > 0 && (
          <div className="lot-grid three-up">
            {data.placed.map((lot) => <LotCard key={lot.id} lot={lot} />)}
          </div>
        )}
      </section>

      <section className="spaced">
        <h2>Collections you bid on</h2>
        <p className="muted">Collections where you placed a bid on at least one lot.</p>
        {data.placedCollections.length === 0 && <p>You have not bid on a collection yet.</p>}
        {data.placedCollections.length > 0 && (
          <div className="lot-grid three-up">
            {data.placedCollections.map((collection) => (
              <CollectionCard key={collection.id} collection={collection} />
            ))}
          </div>
        )}
      </section>

      <section className="spaced">
        <h2>Lots you won</h2>
        {data.wins.length === 0 && <p>You have not won a lot yet.</p>}
        {data.wins.length > 0 && (
          <div className="data-scroll">
          <table className="data">
            <thead>
              <tr><th>Lot</th><th>Hammer</th><th>Winner</th><th>Your last bid</th><th>Settle by</th><th></th></tr>
            </thead>
            <tbody>
              {data.wins.map((lot) => (
                <tr key={lot.id}>
                  <td data-label="Lot">{lot.title}</td>
                  <td data-label="Hammer">{money(lot.currentPrice)}</td>
                  <td data-label="Winner">{inferredWinner(lot, user?.username) || "You"}</td>
                  <td data-label="Your last bid">{lot.myLastBidAmount != null ? money(lot.myLastBidAmount) : "—"}</td>
                  <td data-label="Settle by">{lot.paid ? "Paid" : (paymentDueLabel(lot) || "—")}</td>
                  <td data-label="Actions">{lot.paid ? <span className="muted">Paid</span> : <Link className="text-link" to={`/auctions/${lot.id}/pay`}>Pay</Link>}</td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        )}
      </section>

      <section className="spaced">
        <h2>Lots you lost</h2>
        {data.losses.length === 0 && <p>You have not lost a lot yet.</p>}
        {data.losses.length > 0 && (
          <div className="data-scroll">
          <table className="data">
            <thead>
              <tr><th>Lot</th><th>Hammer</th><th>Winner</th><th>Your last bid</th><th></th></tr>
            </thead>
            <tbody>
              {data.losses.map((lot) => (
                <tr key={lot.id}>
                  <td data-label="Lot">{lot.title}</td>
                  <td data-label="Hammer">{money(lot.currentPrice)}</td>
                  <td data-label="Winner">{inferredWinner(lot, user?.username) || "—"}</td>
                  <td data-label="Your last bid">{lot.myLastBidAmount != null ? money(lot.myLastBidAmount) : "—"}</td>
                  <td data-label="Actions"><Link className="text-link" to={`/auctions/${lot.id}`}>View lot</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        )}
      </section>
    </main>
  );
}
