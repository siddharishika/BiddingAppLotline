import { useEffect, useState } from "react";
import { listCollections } from "../api";
import CollectionCard from "../components/CollectionCard";
import { useToast } from "../toast";

export default function CollectionsPage() {
  const toast = useToast();
  const [collections, setCollections] = useState([]);

  useEffect(() => {
    listCollections()
      .then(setCollections)
      .catch((err) => toast.error(err.message));
  }, [toast]);

  return (
    <main className="wrap section">
      <div className="section-head">
        <div>
          <p className="eyebrow">Catalogue</p>
          <h1>Collection catalogue</h1>
          <p className="lede">
            Every posting is a collection — one lot or several. Open a collection to preview its lots.
          </p>
        </div>
      </div>
      {collections.length === 0 && <p className="muted">No collections listed yet.</p>}
      <div className="lot-grid">
        {collections.map((collection) => (
          <CollectionCard key={collection.id} collection={collection} />
        ))}
      </div>
    </main>
  );
}
