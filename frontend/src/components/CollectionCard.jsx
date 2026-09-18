import { Link } from "react-router-dom";
import { liveLotCountLabel, lotCountLabel, myBidCountLabel, pillClass, statusLabel } from "../format";

function collectionShelf(collection) {
  const categories = (collection.categories || []).filter(Boolean);
  return categories.join(" · ") || "Collection";
}

function collectionCountLine(collection) {
  if (collection.myBidCount != null) {
    return myBidCountLabel(collection.myBidCount, collection.lotCount);
  }
  if (Number(collection.lotCount) > 1) {
    return liveLotCountLabel(collection.liveLotCount, collection.lotCount);
  }
  return lotCountLabel(collection.lotCount);
}

export default function CollectionCard({ collection }) {
  const images = (collection.imageUrls && collection.imageUrls.length > 0)
    ? collection.imageUrls
    : (collection.imageUrl ? [collection.imageUrl] : []);
  const mosaicCount = images.length > 1 ? Math.min(images.length, 4) : 0;
  const description = (collection.description || "").trim();

  return (
    <Link className="lot-card" to={`/collections/${collection.id}`}>
      <div className={mosaicCount ? `lot-image collection-mosaic mosaic-${mosaicCount}` : "lot-image"}>
        {mosaicCount
          ? images.slice(0, mosaicCount).map((url) => (
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
          <span className="muted">{collectionShelf(collection)}</span>
          <span className={pillClass(collection.status)}>{statusLabel(collection.status)}</span>
        </div>
        <h3>{collection.name}</h3>
        {description && <p className="lot-blurb">{description}</p>}
        <p className="bidder-count">{collectionCountLine(collection)}</p>
      </div>
    </Link>
  );
}
