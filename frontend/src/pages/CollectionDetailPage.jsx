import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getCollection } from "../api";
import LotCard from "../components/LotCard";
import { lotCountLabel, pillClass, statusLabel } from "../format";
import { useToast } from "../toast";

export default function CollectionDetailPage() {
  const { id } = useParams();
  const toast = useToast();
  const [collection, setCollection] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    getCollection(id)
      .then((data) => {
        if (!cancelled) {
          setCollection(data);
        }
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
  }, [id, toast]);

  if (!collection && !error) {
    return <main className="wrap page-status"><p className="muted">Loading collection…</p></main>;
  }
  if (!collection) {
    return <main className="wrap page-status"><div className="flash flash-err">{error}</div></main>;
  }

  return (
    <main className="wrap section">
      <p className="crumb"><Link to="/collections">Collection catalogue</Link> / {collection.name}</p>
      <div className="section-head">
        <div>
          <p className="eyebrow">Collection</p>
          <h1>{collection.name}</h1>
          <p className="lede">
            Offered by <strong>{collection.sellerUsername}</strong>
            {" · "}
            {lotCountLabel(collection.lotCount)}
          </p>
          {collection.description && <p className="body-copy collection-copy">{collection.description}</p>}
        </div>
        <span className={pillClass(collection.status)}>{statusLabel(collection.status)}</span>
      </div>
      <div className="lot-grid">
        {(collection.lots || []).map((lot) => <LotCard key={lot.id} lot={lot} />)}
      </div>
    </main>
  );
}
