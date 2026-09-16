import { useEffect, useMemo, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { getPayments } from "../api";
import { formatWhen, money, paymentDueLabel } from "../format";
import { useToast } from "../toast";

export default function PaymentsPage() {
  const toast = useToast();
  const location = useLocation();
  const [data, setData] = useState({ payments: [], wins: [] });

  useEffect(() => {
    getPayments()
      .then((payload) => {
        // Receipts and awaiting lots come from the database only.
        setData({
          payments: payload.payments ?? [],
          wins: payload.wins ?? [],
        });
      })
      .catch((err) => toast.error(err.message));
  }, [toast, location.key]);

  const retryAuctionIds = useMemo(() => new Set(
    data.payments
      .filter((payment) => payment.status === "FAILED" && payment.auctionId != null)
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
                    : <code>{payment.gatewayTransactionId || "—"}</code>}
                </td>
                <td data-label="Card">{payment.lastFour ? `•••• ${payment.lastFour}` : "—"}</td>
                <td data-label="When">{payment.createdAt ? formatWhen(payment.createdAt) : "—"}</td>
                <td className="col-actions" data-label="Actions">
                  {payment.status === "FAILED" && payment.auctionId != null
                    && data.wins.some((lot) => lot.id === payment.auctionId) && (
                    <Link className="btn btn-gold btn-compact" to={`/auctions/${payment.auctionId}/pay`}>
                      Retry
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
