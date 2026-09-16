import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { createAuction, createCollection, getCategories } from "../api";
import { formatWhen, toDatetimeLocal } from "../format";
import { useToast } from "../toast";

let lotSeq = 0;

function emptyLot() {
  lotSeq += 1;
  return {
    id: `lot-${lotSeq}`,
    title: "",
    description: "",
    category: "",
    imageUrl: "",
    startingPrice: "100.00",
    minIncrement: "10.00",
  };
}

export default function NewLotPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [categories, setCategories] = useState([]);
  const [busy, setBusy] = useState(false);
  const [mode, setMode] = useState("single");
  const [collectionName, setCollectionName] = useState("");
  const [collectionDescription, setCollectionDescription] = useState("");
  const [startTime, setStartTime] = useState(toDatetimeLocal());
  const [durationMinutes, setDurationMinutes] = useState("60");
  const [lots, setLots] = useState([emptyLot()]);
  const [flashId, setFlashId] = useState(null);
  const [status, setStatus] = useState("");
  const titleRefs = useRef({});

  useEffect(() => {
    getCategories().then(setCategories).catch((err) => toast.error(err.message));
  }, [toast]);

  useEffect(() => {
    if (!flashId) {
      return undefined;
    }
    titleRefs.current[flashId]?.focus();
    const timer = window.setTimeout(() => setFlashId(null), 900);
    return () => window.clearTimeout(timer);
  }, [flashId]);

  function updateLot(id, field, value) {
    setLots((current) => current.map((lot) => (lot.id === id ? { ...lot, [field]: value } : lot)));
  }

  function setListingMode(next) {
    setMode(next);
    if (next === "collection") {
      setLots((current) => (current.length < 2 ? [...current, emptyLot()] : current));
      setStatus("Collection mode. Add or remove lots here — they are not saved until you list.");
    } else {
      setLots((current) => [current[0] ?? emptyLot()]);
      setStatus("");
    }
  }

  function addLot(event) {
    event.preventDefault();
    const next = emptyLot();
    setLots((current) => [...current, next]);
    setFlashId(next.id);
    setStatus("Lot added.");
  }

  function removeLot(event, id) {
    event.preventDefault();
    const remaining = lots.filter((lot) => lot.id !== id);
    if (remaining.length < 2) {
      setStatus("A collection needs at least two lots.");
      return;
    }
    setLots(remaining);
    setStatus("Lot removed.");
  }

  const openingAt = new Date(startTime);
  const opensLater = !Number.isNaN(openingAt.getTime()) && openingAt.getTime() > Date.now() + 15000;

  function validateLot(lot) {
    if (!lot.title.trim() || !lot.description.trim() || !lot.category) {
      toast.error("Each lot needs a title, description, and category.");
      return false;
    }
    if (Number(lot.startingPrice) < 1) {
      toast.error("Starting price must be at least $1.00");
      return false;
    }
    if (Number(lot.minIncrement) < 1) {
      toast.error("Minimum increment must be at least $1.00");
      return false;
    }
    return true;
  }

  function onFormKeyDown(event) {
    if (event.key !== "Enter") {
      return;
    }
    const tag = event.target.tagName;
    if (tag === "TEXTAREA" || tag === "BUTTON") {
      return;
    }
    event.preventDefault();
  }

  async function onSubmit(event) {
    event.preventDefault();
    if (Number(durationMinutes) < 1) {
      toast.error("Auction must run for at least 1 minute");
      return;
    }
    if (Number.isNaN(openingAt.getTime())) {
      toast.error("Choose when bidding opens");
      return;
    }
    if (mode === "collection") {
      if (!collectionName.trim()) {
        toast.error("Name this one-time collection after what most of the lots are about.");
        return;
      }
      if (!collectionDescription.trim()) {
        toast.error("Write a few sentences describing the collection as a whole.");
        return;
      }
      if (lots.length < 2) {
        toast.error("A collection needs at least two lots.");
        return;
      }
    }
    if (!lots.every(validateLot)) {
      return;
    }
    setBusy(true);
    try {
      const shared = {
        startTime: openingAt.toISOString(),
        durationMinutes: Number(durationMinutes),
      };
      const lotPayload = (lot) => ({
        title: lot.title,
        description: lot.description,
        category: lot.category,
        imageUrl: lot.imageUrl || null,
        startingPrice: Number(lot.startingPrice),
        minIncrement: Number(lot.minIncrement),
        ...shared,
      });
      if (mode === "collection") {
        const created = await createCollection({
          name: collectionName.trim(),
          description: collectionDescription.trim(),
          ...shared,
          lots: lots.map(lotPayload),
        });
        toast.success(`Collection “${collectionName.trim()}” listed with ${created.length} lots.`);
        navigate(`/auctions/${created[0].id}`);
      } else {
        const created = await createAuction(lotPayload(lots[0]));
        if (created.status === "SCHEDULED") {
          toast.success(`Lot scheduled. Bidding opens ${formatWhen(created.startTime)}.`);
        } else {
          toast.success("Lot listed. Bidding is open.");
        }
        navigate(`/auctions/${created.id}`);
      }
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className={mode === "collection" ? "wrap section" : "wrap narrow section"}>
      <p className="eyebrow">Consignment</p>
      <h1>List a lot</h1>
      <p className="lede">
        Category is the wide shelf (Art, Watches). Every listing belongs to a collection:
        a single lot is a collection of one, named after the lot. Two or more lots share a name you choose.
      </p>
      <form className="stack" onSubmit={onSubmit} onKeyDown={onFormKeyDown} noValidate>
        <fieldset className="listing-choice">
          <legend>How are you listing?</legend>
          <label>
            <input
              type="radio"
              name="listing-mode"
              checked={mode === "single"}
              onChange={() => setListingMode("single")}
            />
            <span>Single lot</span>
          </label>
          <label>
            <input
              type="radio"
              name="listing-mode"
              checked={mode === "collection"}
              onChange={() => setListingMode("collection")}
            />
            <span>Collection (two or more lots)</span>
          </label>
        </fieldset>

        {mode === "collection" && (
          <>
            <label>Collection name
              <input
                value={collectionName}
                onChange={(event) => setCollectionName(event.target.value)}
                placeholder="e.g. A weekend by the sea"
              />
            </label>
            <label>Collection description
              <textarea
                rows="5"
                value={collectionDescription}
                onChange={(event) => setCollectionDescription(event.target.value)}
                placeholder="Three or four sentences about why these lots belong together."
              />
            </label>
          </>
        )}

        <div className="form-row">
          <label>Opens at
            <input type="datetime-local" value={startTime} onChange={(event) => setStartTime(event.target.value)} />
          </label>
          <label>Duration (minutes)
            <input type="number" min="1" value={durationMinutes} onChange={(event) => setDurationMinutes(event.target.value)} />
          </label>
        </div>

        {mode === "collection" && (
          <p className="hint" aria-live="polite">
            {status
              ? `${status} ${lots.length} lots in this collection.`
              : `${lots.length} lots in this collection. Add and remove stay on this page until you list.`}
          </p>
        )}

        {lots.map((lot, index) => (
          <fieldset
            className={`lot-draft${flashId === lot.id ? " lot-draft-flash" : ""}`}
            key={lot.id}
          >
            <legend>{mode === "collection" ? `Lot ${index + 1}` : "The lot"}</legend>
            {mode === "collection" && lots.length > 2 && (
              <button
                className="btn btn-ghost btn-compact"
                type="button"
                onClick={(event) => removeLot(event, lot.id)}
              >
                Remove this lot
              </button>
            )}
            <label>Title
              <input
                ref={(node) => {
                  titleRefs.current[lot.id] = node;
                }}
                value={lot.title}
                onChange={(event) => updateLot(lot.id, "title", event.target.value)}
              />
            </label>
            <label>Description
              <textarea
                rows="6"
                value={lot.description}
                onChange={(event) => updateLot(lot.id, "description", event.target.value)}
                placeholder="Three or four sentences so a buyer can tell what they are bidding on."
              />
            </label>
            <label>Category
              <select value={lot.category} onChange={(event) => updateLot(lot.id, "category", event.target.value)}>
                <option value="" disabled>Select</option>
                {categories.map((category) => (
                  <option key={category} value={category}>{category}</option>
                ))}
              </select>
            </label>
            <label>Image URL (optional)
              <input type="url" placeholder="https://" value={lot.imageUrl} onChange={(event) => updateLot(lot.id, "imageUrl", event.target.value)} />
            </label>
            <div className="form-row">
              <label>Starting price
                <input type="number" step="0.01" value={lot.startingPrice} onChange={(event) => updateLot(lot.id, "startingPrice", event.target.value)} />
              </label>
              <label>Minimum increment
                <input type="number" step="0.01" value={lot.minIncrement} onChange={(event) => updateLot(lot.id, "minIncrement", event.target.value)} />
              </label>
            </div>
          </fieldset>
        ))}

        {mode === "collection" && (
          <button className="btn btn-ghost" type="button" onClick={addLot}>
            Add another lot
          </button>
        )}

        <button className="btn btn-gold" type="submit" disabled={busy}>
          {busy ? "Saving…" : mode === "collection" ? "List collection" : opensLater ? "Schedule lot" : "Open bidding"}
        </button>
      </form>
    </main>
  );
}
