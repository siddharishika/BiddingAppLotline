import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { getAuction, payAuction } from "../api";
import { formatWhen, money } from "../format";
import { useToast } from "../toast";

export default function PayPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const [lot, setLot] = useState(null);
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({
    cardholderName: "",
    cardNumber: "",
    expiry: "",
    cvc: "",
  });

  useEffect(() => {
    getAuction(id)
      .then((data) => {
        if (!data.winner || data.paid) {
          navigate(data.paid ? "/payments" : `/auctions/${id}`, { replace: true });
          return;
        }
        setLot(data);
      })
      .catch((err) => toast.error(err.message));
  }, [id, navigate, toast]);

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  async function onSubmit(event) {
    event.preventDefault();
    setBusy(true);
    try {
      await payAuction(id, {
        ...form,
        cardNumber: form.cardNumber.replace(/\s/g, ""),
      });
      toast.success("Payment authorized. Your receipt is in Payments.");
      // Receipt is already persisted by the API; Payments reloads from the database.
      navigate("/payments", { replace: true });
    } catch (err) {
      toast.error(err.message || "Payment declined");
      // Failed attempts are persisted server-side; Payments reloads from the database.
      navigate("/payments", { replace: true });
    } finally {
      setBusy(false);
    }
  }

  if (!lot) {
    return <main className="wrap page-status"><p className="muted">Loading checkout…</p></main>;
  }

  return (
    <main className="wrap narrow section">
      <p className="eyebrow">LotlinePay</p>
      <h1>Settle this lot</h1>
      {lot && (
        <p className="lede">
          <strong>{lot.title}</strong> · <span>{money(lot.currentPrice)}</span>
        </p>
      )}
      {lot.paymentDueAt && (
        <p className="hint">Settle within 7 days of the hammer. Window closes {formatWhen(lot.paymentDueAt)}.</p>
      )}
      <p className="hint">Card data is sent to the payment gateway and is not stored. Only the last four digits and a transaction id are kept.</p>
      <form className="stack" onSubmit={onSubmit}>
        <label>Name on card
          <input value={form.cardholderName} onChange={(event) => update("cardholderName", event.target.value)} autoComplete="cc-name" required />
        </label>
        <label>Card number
          <input
            value={form.cardNumber}
            onChange={(event) => update("cardNumber", event.target.value)}
            inputMode="numeric"
            autoComplete="cc-number"
            placeholder="4242424242424242"
            required
          />
        </label>
        <div className="form-row">
          <label>Expiry
            <input value={form.expiry} onChange={(event) => update("expiry", event.target.value)} placeholder="MM/YY" autoComplete="cc-exp" required />
          </label>
          <label>CVC
            <input value={form.cvc} onChange={(event) => update("cvc", event.target.value)} autoComplete="cc-csc" required />
          </label>
        </div>
        <button className="btn btn-gold" type="submit" disabled={busy}>
          {busy ? "Authorizing…" : "Authorize payment"}
        </button>
      </form>
    </main>
  );
}
