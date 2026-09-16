import { useEffect, useState } from "react";
import { listAuctions, listCollections } from "../api";
import CollectionCard from "../components/CollectionCard";
import LotCard from "../components/LotCard";
import { useToast } from "../toast";

const FILTERS = {
  LIVE: {
    status: "LIVE",
    eyebrow: "On the block",
    title: "Live lots",
    lede: "These rooms are open. The current bid and clock update as they happen.",
    empty: "No live lots right now. Check Upcoming, or list a lot to open a room.",
    collectionsTitle: "Live collections",
    collectionsLede: "Multi-lot postings with at least one room still open on the floor.",
    collectionsEmpty: "No live collections right now.",
  },
  SCHEDULED: {
    status: "SCHEDULED",
    eyebrow: "Preview",
    title: "Upcoming lots",
    lede: "Bidding has not opened yet. Each card shows when the room starts.",
    empty: "No lots waiting to open. List a lot with a future opening time.",
  },
  ALL: {
    status: null,
    eyebrow: "Catalogue",
    title: "Lot catalogue",
    lede: "Every lot with a short preview — live, upcoming, and closed.",
    empty: "Nothing listed yet.",
  },
};

export default function AuctionsPage({ filter = "ALL" }) {
  const toast = useToast();
  const [lots, setLots] = useState([]);
  const [collections, setCollections] = useState([]);
  const view = FILTERS[filter] ?? FILTERS.ALL;
  const showLiveCollections = filter === "LIVE";

  useEffect(() => {
    listAuctions(view.status)
      .then(setLots)
      .catch((err) => toast.error(err.message));
  }, [toast, view.status]);

  useEffect(() => {
    if (!showLiveCollections) {
      setCollections([]);
      return undefined;
    }
    listCollections("LIVE")
      .then(setCollections)
      .catch((err) => toast.error(err.message));
    return undefined;
  }, [toast, showLiveCollections]);

  return (
    <main className="wrap section">
      <div className="section-head">
        <div>
          <p className="eyebrow">{view.eyebrow}</p>
          <h1>{view.title}</h1>
          <p className="lede">{view.lede}</p>
        </div>
      </div>

      {showLiveCollections && (
        <section>
          <h2>{view.collectionsTitle}</h2>
          <p className="muted">{view.collectionsLede}</p>
          {collections.length === 0 && <p className="muted">{view.collectionsEmpty}</p>}
          {collections.length > 0 && (
            <div className="lot-grid">
              {collections.map((collection) => (
                <CollectionCard key={collection.id} collection={collection} />
              ))}
            </div>
          )}
        </section>
      )}

      <section className={showLiveCollections ? "spaced" : undefined}>
        {showLiveCollections && <h2>Live lots</h2>}
        {lots.length === 0 && <p className="muted">{view.empty}</p>}
        <div className="lot-grid">
          {lots.map((lot) => <LotCard key={lot.id} lot={lot} />)}
        </div>
      </section>
    </main>
  );
}
