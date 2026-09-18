import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { completeCheckout, getPayments } from "../api";
import { formatWhen, money, paymentDueLabel } from "../format";
import { useToast } from "../toast";

export default function PaymentsPage() {
  const toast = useToast();
  const location = useLocation();
  const navigate = useNavigate();
  const completing = useRef(false);
  const [data, setData] = useState({ payments: [], wins: [] });

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const sessionId = params.get("session_id");
    if (params.get("checkout") === "success" && sessionId) {
      if (completing.current) {
        return;
      }
      completing.current = true;
      completeCheckout(sessionId)
        .then(() => toast.success("Payment authorized. Your receipt is in Payments."))
        .catch((err) => toast.error(err.message || "Payment is still processing"))
        .finally(() => {
          navigate("/payments", { replace: true });
        });
      return;
    }

    getPayments()
      .then((payload) => {
        setData({
          payments: payload.payments ?? [],
          wins: payload.wins ?? [],
        });
      })
      .catch((err) => toast.error(err.message));
  }, [toast, location.key, location.search, navigate]);

  const retryAuctionIds = useMemo(() => new Set(
    data.payments
      .filter((payment) => (
        payment.status === "FAILED"
        || payment.status === "PENDING"
        || payment.status === "EXPIRED"
      ) && payment.auctionId != null)
      .map((payment) => payment.auctionId)
  ), [data.payments]);

  return (
    <main className="wrap section">
      <p className="eyebrow">Settlements</p>
      <h1>Payments</h1>

      <h2>Receipts</h2>
      <p className="muted">Successful charges and failed attempts stored for your account.</p>
      {data.payments.length === 0 && <p>No charges yet.</p>}
      {data.payments.length > 0 && (
        <div className="data-scroll">
        <table className="data">
          <thead>
            <tr>
              <th>Lot</th>
              <th>Amount</th>
              <th>Status</th>
              <th>Details</th>
              <th>Card</th>
              <th>When</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {data.payments.map((payment) => (
              <tr key={payment.id}>
                <td data-label="Lot">{payment.auctionTitle}</td>
                <td data-label="Amount">{money(payment.amount)}</td>
                <td data-label="Status">{payment.status}</td>
                <td data-label="Details">
                  {payment.status === "FAILED"
                    ? (payment.failureReason || "Payment declined")
                    : payment.status === "EXPIRED"
                      ? (payment.failureReason || "Checkout expired")
                      : payment.status === "PENDING"
                        ? "Checkout started"
                        : <code>{payment.gatewayTransactionId || "—"}</code>}
                </td>
                <td data-label="Card">{payment.lastFour ? `•••• ${payment.lastFour}` : "—"}</td>
                <td data-label="When">{payment.createdAt ? formatWhen(payment.createdAt) : "—"}</td>
                <td className="col-actions" data-label="Actions">
                  {(payment.status === "FAILED" || payment.status === "PENDING" || payment.status === "EXPIRED")
                    && payment.auctionId != null
                    && data.wins.some((lot) => lot.id === payment.auctionId) && (
                    <Link className="btn btn-gold btn-compact" to={`/auctions/${payment.auctionId}/pay`}>
                      {payment.status === "PENDING" ? "Continue" : "Retry"}
                    </Link>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        </div>
      )}

      <h2 className="spaced">Awaiting payment</h2>
      <p className="muted">Winners have 7 days after the hammer to settle. Unpaid lots return as unsold for the issuer.</p>
      {data.wins.length === 0 && <p>Nothing to settle.</p>}
      {data.wins.length > 0 && (
        <ul className="plain awaiting-list">
          {data.wins.map((lot) => {
            const retry = retryAuctionIds.has(lot.id);
            return (
              <li key={lot.id}>
                <div className="awaiting-copy">
                  <strong className="awaiting-title">{lot.title}</strong>
                  <span className="muted">
                    {money(lot.currentPrice)}
                    {paymentDueLabel(lot) ? ` · ${paymentDueLabel(lot)}` : ""}
                  </span>
                </div>
                <Link className="btn btn-gold btn-compact" to={`/auctions/${lot.id}/pay`}>
                  {retry ? "Retry" : "Pay"}
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </main>
  );
}
