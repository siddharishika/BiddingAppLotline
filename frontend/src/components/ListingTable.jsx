import { useState } from "react";
import Timer from "./Timer";
import { canWithdraw, isUnsold, isWithdrawn } from "../listingGroups";
import { bidderLabel, money, pillClass, statusLabel } from "../format";

function lotHasActions(lot, onWithdraw, onReopen, onDelete) {
  return Boolean(
    (onWithdraw && canWithdraw(lot))
    || (onReopen && (isWithdrawn(lot) || isUnsold(lot)))
    || (onDelete && isWithdrawn(lot))
  );
}

export default function ListingTable({ lots, busy, onWithdraw, onReopen, onDelete }) {
  const [openId, setOpenId] = useState(null);
  const showActions = lots.some((lot) => lotHasActions(lot, onWithdraw, onReopen, onDelete));

  function toggleDescription(lot) {
    setOpenId((current) => (current === lot.id ? null : lot.id));
  }

  return (
    <div className="data-scroll">
    <table className={`data${showActions ? "" : " data-no-actions"}`}>
      <thead>
        <tr>
          <th>Lot</th>
          <th>Status</th>
          <th>Price</th>
          <th>Bidders</th>
          <th>Clock</th>
          {showActions && <th className="col-actions"></th>}
        </tr>
      </thead>
      <tbody>
        {lots.map((lot) => {
          const expanded = openId === lot.id;
          const actions = [];
          if (onWithdraw && canWithdraw(lot)) {
            actions.push(
              <button key="withdraw" className="text-link" type="button" disabled={busy} onClick={() => onWithdraw(lot)}>
                Withdraw
              </button>
            );
          }
          if (onReopen && (isWithdrawn(lot) || isUnsold(lot))) {
            actions.push(
              <button key="reopen" className="text-link" type="button" disabled={busy} onClick={() => onReopen(lot)}>
                Reopen
              </button>
            );
          }
          if (onDelete && isWithdrawn(lot)) {
            actions.push(
              <button key="delete" className="text-link" type="button" disabled={busy} onClick={() => onDelete(lot)}>
                Delete
              </button>
            );
          }
          return (
            <tr key={lot.id} className={expanded ? "listing-row-open" : undefined}>
              <td data-label="Lot">
                <button
                  className="lot-name-link"
                  type="button"
                  aria-expanded={expanded}
                  onClick={() => toggleDescription(lot)}
                >
                  {lot.title}
                </button>
                {expanded && (
                  <p className="listing-description">{lot.description || "No description on file."}</p>
                )}
              </td>
              <td data-label="Status"><span className={pillClass(lot.status)}>{statusLabel(lot.status)}</span></td>
              <td data-label="Price">
                {lot.status === "SOLD" ? `Hammer ${money(lot.currentPrice)}` : money(lot.currentPrice)}
                {lot.status === "SOLD" && lot.winnerUsername ? ` · Won by ${lot.winnerUsername}` : ""}
              </td>
              <td data-label="Bidders">{bidderLabel(lot.bidderCount)}</td>
              <td data-label="Clock">
                {lot.status === "SCHEDULED" ? (
                  <Timer endTime={lot.startTime} endedLabel="Opening" />
                ) : lot.status === "LIVE" ? (
                  <Timer endTime={lot.endTime} endedLabel="00:00:00" />
                ) : (
                  <span className="muted">—</span>
                )}
              </td>
              {showActions && (
                <td className="col-actions" data-label="Actions">
                  {actions.map((action, index) => (
                    <span key={action.key}>
                      {index > 0 ? " · " : null}
                      {action}
                    </span>
                  ))}
                </td>
              )}
            </tr>
          );
        })}
      </tbody>
    </table>
    </div>
  );
}
