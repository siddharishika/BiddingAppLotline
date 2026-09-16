import { useEffect, useState } from "react";
import { deleteCollection, deleteLot, getMyLots, reopenCollection, reopenLot, withdrawCollection, withdrawLot } from "../api";
import ListingTable from "../components/ListingTable";
import { groupListedLots, canWithdrawCollection, isWithdrawn, splitWithdrawn } from "../listingGroups";
import { useToast } from "../toast";

export default function MyCollectionsPage() {
  const toast = useToast();
  const [lots, setLots] = useState([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    getMyLots()
      .then(setLots)
      .catch((err) => toast.error(err.message));
  }, [toast]);

  async function withdrawOne(lot) {
    if (!window.confirm(`Withdraw “${lot.title}”? It will stay with this collection until you reopen or delete it.`)) {
      return;
    }
    setBusy(true);
    try {
      await withdrawLot(lot.id);
      setLots((current) => current.map((item) => (
        item.id === lot.id ? { ...item, status: "CANCELLED" } : item
      )));
      toast.success("Lot withdrawn.");
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function reopenOne(lot) {
    setBusy(true);
    try {
      await reopenLot(lot.id);
      setLots(await getMyLots());
      toast.success("Lot reopened.");
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function removeOne(lot) {
    if (!window.confirm(`Permanently delete “${lot.title}”? This cannot be undone.`)) {
      return;
    }
    setBusy(true);
    try {
      await deleteLot(lot.id);
      setLots((current) => current.filter((item) => item.id !== lot.id));
      toast.success("Lot deleted.");
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function withdraw(group) {
    if (!window.confirm(`Withdraw collection “${group.collectionName}”? It will move to Withdrew collections until you reopen or delete it.`)) {
      return;
    }
    setBusy(true);
    try {
      await withdrawCollection(group.collectionId);
      const ids = new Set(group.lots.map((lot) => lot.id));
      setLots((current) => current.map((item) => (
        ids.has(item.id) ? { ...item, status: "CANCELLED" } : item
      )));
      toast.success("Collection withdrawn.");
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function reopen(group) {
    if (!window.confirm(`Reopen collection “${group.collectionName}” on the live floor?`)) {
      return;
    }
    setBusy(true);
    try {
      await reopenCollection(group.collectionId);
      setLots(await getMyLots());
      toast.success("Collection reopened.");
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function remove(group) {
    if (!window.confirm(`Permanently delete collection “${group.collectionName}”? This cannot be undone.`)) {
      return;
    }
    setBusy(true);
    try {
      await deleteCollection(group.collectionId);
      const ids = new Set(group.lots.map((lot) => lot.id));
      setLots((current) => current.filter((item) => !ids.has(item.id)));
      toast.success("Collection deleted.");
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  const collections = groupListedLots(lots).collections;
  const { active, withdrawn } = splitWithdrawn(
    collections,
    (group) => group.lots.length > 0 && group.lots.every(isWithdrawn)
  );

  return (
    <main className="wrap section">
      <p className="eyebrow">Consignor desk</p>
      <h1>My collections</h1>
      <p className="lede">
        Multi-lot postings you issued. Withdraw only while every open lot is still upcoming or unsold
        (live rooms stay on the floor). Reopen or delete from Withdrew collections.
      </p>

      <section>
        <h2>My collections</h2>
        {active.length === 0 && <p>You have no active collections.</p>}
        {active.map((group) => (
          <div className="bid-group" key={group.collectionId}>
            <p className="bid-group-label">
              Collection · <strong>{group.collectionName}</strong>
              {canWithdrawCollection(group.lots) && (
                <>
                  {" · "}
                  <button className="text-link" type="button" disabled={busy} onClick={() => withdraw(group)}>
                    Withdraw collection
                  </button>
                </>
              )}
            </p>
            <ListingTable
              lots={group.lots}
              busy={busy}
              onWithdraw={withdrawOne}
              onReopen={reopenOne}
              onDelete={removeOne}
            />
          </div>
        ))}
      </section>

      <section className="spaced">
        <h2>Withdrew collections</h2>
        <p className="muted">Withdrawn collections stay here until you reopen them or delete them permanently.</p>
        {withdrawn.length === 0 && <p>You have not withdrawn a collection yet.</p>}
        {withdrawn.map((group) => (
          <div className="bid-group" key={group.collectionId}>
            <p className="bid-group-label collection-actions">
              <span>Collection · <strong>{group.collectionName}</strong></span>
              <span>
                <button className="text-link" type="button" disabled={busy} onClick={() => reopen(group)}>
                  Reopen collection
                </button>
                {" · "}
                <button className="text-link" type="button" disabled={busy} onClick={() => remove(group)}>
                  Delete collection
                </button>
              </span>
            </p>
            <ListingTable lots={group.lots} busy={busy} />
          </div>
        ))}
      </section>
    </main>
  );
}
