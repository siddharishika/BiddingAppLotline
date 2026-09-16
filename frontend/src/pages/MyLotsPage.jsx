import { useEffect, useState } from "react";
import { deleteLot, getMyLots, reopenLot, withdrawLot as withdrawLotRequest } from "../api";
import ListingTable from "../components/ListingTable";
import { groupListedLots, splitConsignorSingles } from "../listingGroups";
import { useToast } from "../toast";

export default function MyLotsPage() {
  const toast = useToast();
  const [lots, setLots] = useState([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    getMyLots()
      .then(setLots)
      .catch((err) => toast.error(err.message));
  }, [toast]);

  async function withdraw(lot) {
    if (!window.confirm(`Withdraw “${lot.title}”? It will move to Withdrew lots until you reopen or delete it.`)) {
      return;
    }
    setBusy(true);
    try {
      await withdrawLotRequest(lot.id);
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

  async function reopen(lot) {
    if (!window.confirm(`Reopen “${lot.title}” on the live floor?`)) {
      return;
    }
    setBusy(true);
    try {
      await reopenLot(lot.id);
      const refreshed = await getMyLots();
      setLots(refreshed);
      toast.success("Lot reopened.");
    } catch (err) {
      toast.error(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function remove(lot) {
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

  const { active, unsold, withdrawn } = splitConsignorSingles(groupListedLots(lots).singles);

  return (
    <main className="wrap section">
      <p className="eyebrow">Consignor desk</p>
      <h1>My lots</h1>
      <p className="lede">
        Single lots you issued. Unsold and upcoming lots can be withdrawn; reopen unsold lots here, or reopen withdrawn lots from Withdrew lots.
      </p>

      <section>
        <h2>My lots</h2>
        {active.length === 0 && <p>You have no active single lots.</p>}
        {active.length > 0 && (
          <ListingTable lots={active} busy={busy} onWithdraw={withdraw} />
        )}
      </section>

      <section className="spaced">
        <h2>Unsold lots</h2>
        <p className="muted">Lots that closed without a settled sale. Reopen one to put it back on the floor, or withdraw it.</p>
        {unsold.length === 0 && <p>You have no unsold lots.</p>}
        {unsold.length > 0 && (
          <ListingTable lots={unsold} busy={busy} onWithdraw={withdraw} onReopen={reopen} />
        )}
      </section>

      <section className="spaced">
        <h2>Withdrew lots</h2>
        <p className="muted">Withdrawn lots stay here until you reopen them or delete them permanently.</p>
        {withdrawn.length === 0 && <p>You have not withdrawn a lot yet.</p>}
        {withdrawn.length > 0 && (
          <ListingTable lots={withdrawn} busy={busy} onReopen={reopen} onDelete={remove} />
        )}
      </section>
    </main>
  );
}
