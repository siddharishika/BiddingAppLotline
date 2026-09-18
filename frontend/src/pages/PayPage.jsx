import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { getAuction, getPaymentGateway, startCheckout } from "../api";
import { formatWhen, money } from "../format";
import { useToast } from "../toast";

export default function PayPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const canceledToast = useRef(false);
  const [lot, setLot] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (searchParams.get("checkout") !== "canceled" || canceledToast.current) {
      return;
    }
    canceledToast.current = true;
    toast.error("Checkout was canceled. Stripe did not confirm a charge.");
    const next = new URLSearchParams(searchParams);
    next.delete("checkout");
    setSearchParams(next, { replace: true });
  }, [searchParams, setSearchParams, toast]);

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

  async function onHostedCheckout(event) {
    event.preventDefault();
    setBusy(true);
    try {
      const gateway = await getPaymentGateway();
      if (gateway?.provider !== "stripe") {
        throw new Error("Stripe is not connected on this server. No payment was recorded.");
      }
      const session = await startCheckout(id);
      if (!session?.url || !session.url.startsWith("https://checkout.stripe.com/")) {
        throw new Error("Stripe did not return a checkout URL. No payment was recorded.");
      }
      window.location.assign(session.url);
    } catch (err) {
      toast.error(err.message || "Could not start Stripe Checkout");
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
      <p className="lede">
        <strong>{lot.title}</strong> · <span>{money(lot.currentPrice)}</span>
      </p>
      {lot.paymentDueAt && (
        <p className="hint">Settle within 7 days of the hammer. Window closes {formatWhen(lot.paymentDueAt)}.</p>
      )}
      <form className="stack" onSubmit={onHostedCheckout}>
        <p className="hint">
          Opening this page does not start a payment. Continue only asks Stripe for a Checkout URL;
          Lotline stores a receipt after Stripe confirms the charge, not when you click the button.
        </p>
        <p className="hint">
          Card details are entered on Stripe. Lotline never sees the full card number.
          Test mode card: 4242 4242 4242 4242.
        </p>
        <button className="btn btn-gold" type="submit" disabled={busy}>
          {busy ? "Asking Stripe for checkout…" : "Continue to Stripe"}
        </button>
      </form>
    </main>
  );
}
