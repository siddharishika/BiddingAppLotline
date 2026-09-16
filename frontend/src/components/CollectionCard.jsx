import { Link } from "react-router-dom";
import { liveLotCountLabel, lotCountLabel, myBidCountLabel, pillClass, statusLabel } from "../format";

export default function CollectionCard({ collection }) {
  const images = (collection.imageUrls && collection.imageUrls.length > 0)
    ? collection.imageUrls
    : (collection.imageUrl ? [collection.imageUrl] : []);
  const mosaic = images.length > 1;
  const countLine = collection.myBidCount != null
    ? myBidCountLabel(collection.myBidCount, collection.lotCount)
    : collection.liveLotCount != null && collection.liveLotCount > 0
      ? liveLotCountLabel(collection.liveLotCount, collection.lotCount)
      : lotCountLabel(collection.lotCount);

  return (
    <Link className="lot-card" to={`/collections/${collection.id}`}>
      <div className={mosaic ? "lot-image collection-mosaic" : "lot-image"}>
        {mosaic
          ? images.map((url) => (
              <span key={url} style={{ backgroundImage: `url('${url}')` }} />
            ))
          : (
              <span
                className="collection-cover"
                style={images[0] ? { backgroundImage: `url('${images[0]}')` } : undefined}
              />
            )}
      </div>
      <div className="lot-body">
        <div className="lot-top">
          <span className="muted">{(collection.categories || []).join(" · ") || "Collection"}</span>
          <span className={pillClass(collection.status)}>{statusLabel(collection.status)}</span>
        </div>
        <h3>{collection.name}</h3>
        {collection.description && <p className="lot-blurb">{collection.description}</p>}
        <p className="bidder-count">{countLine}</p>
      </div>
    </Link>
  );
}
