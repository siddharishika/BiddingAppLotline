export function money(value) {
  const amount = Number(value ?? 0);
  return `$${amount.toFixed(2)}`;
}

export function formatWhen(iso) {
  if (!iso) {
    return "";
  }
  return new Date(iso).toLocaleString(undefined, {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function formatHistoryTime(iso) {
  if (!iso) {
    return "";
  }
  return new Date(iso).toLocaleString(undefined, {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

export function toDatetimeLocal(value = new Date()) {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  const pad = (n) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function nextMinimum(currentPrice, minIncrement) {
  return Number(currentPrice ?? 0) + Number(minIncrement ?? 0);
}

export function lotCountLabel(count) {
  const n = Number(count ?? 0);
  if (n === 1) {
    return "1 lot";
  }
  return `${n} lots`;
}

export function liveLotCountLabel(liveCount, lotCount) {
  const live = Number(liveCount ?? 0);
  const lots = Number(lotCount ?? 0);
  if (live <= 0) {
    return lotCountLabel(lots);
  }
  const livePhrase = live === 1 ? "1 lot live" : `${live} lots live`;
  if (lots <= 0) {
    return livePhrase;
  }
  return `${livePhrase} out of ${lots}`;
}

export function myBidCountLabel(bidCount, lotCount) {
  const bids = Number(bidCount ?? 0);
  const lots = Number(lotCount ?? 0);
  if (bids <= 0) {
    return lotCountLabel(lots);
  }
  return `You bid on ${bids} of ${lots} ${lots === 1 ? "lot" : "lots"}`;
}

export function lotShelf(lot) {
  if (lot?.collectionName) {
    return `${lot.collectionName} · ${lot.category}`;
  }
  return lot?.category ?? "";
}

export function bidderLabel(count) {
  const n = Number(count ?? 0);
  if (n <= 0) {
    return "No bidders yet";
  }
  if (n === 1) {
    return "1 bidder";
  }
  return `${n} bidders`;
}

export function statusLabel(status) {
  if (status === "SCHEDULED") {
    return "Upcoming";
  }
  if (status === "ENDED") {
    return "Unsold";
  }
  if (status === "CANCELLED") {
    return "Withdrawn";
  }
  return status;
}

export function pillClass(status) {
  if (status === "LIVE") {
    return "pill pill-live";
  }
  if (status === "SOLD") {
    return "pill pill-sold";
  }
  if (status === "CANCELLED") {
    return "pill pill-withdrawn";
  }
  return "pill pill-soon";
}

export function winnerLabel(username, currentUsername) {
  if (!username) {
    return null;
  }
  if (currentUsername && username === currentUsername) {
    return "You";
  }
  return username;
}

export function displayLot(lot) {
  if (!lot) {
    return lot;
  }
  const expired = lot.status === "LIVE" && lot.endTime && Date.parse(lot.endTime) <= Date.now();
  if (!expired) {
    return lot;
  }
  return {
    ...lot,
    status: Number(lot.bidderCount) > 0 ? "SOLD" : "ENDED",
  };
}

export function inferredWinner(lot, currentUsername) {
  const labeled = winnerLabel(lot.winnerUsername, currentUsername);
  if (labeled) {
    return labeled;
  }
  if (lot.status === "SOLD" && lot.myLastBidAmount != null
      && Number(lot.myLastBidAmount) === Number(lot.currentPrice)) {
    return currentUsername ? "You" : null;
  }
  return null;
}

export function priceCaption(status) {
  if (status === "SCHEDULED") {
    return "Starting";
  }
  if (status === "SOLD") {
    return "Hammer";
  }
  if (status === "ENDED") {
    return "Passed";
  }
  if (status === "CANCELLED") {
    return "Withdrawn";
  }
  return "Current";
}

function pad(value) {
  return String(value).padStart(2, "0");
}

export function remainingLabel(endIso, endedLabel = "Closed") {
  const remaining = Date.parse(endIso) - Date.now();
  if (!endIso || Number.isNaN(remaining) || remaining <= 0) {
    return endedLabel;
  }
  const total = Math.floor(remaining / 1000);
  const days = Math.floor(total / 86400);
  const hours = Math.floor((total % 86400) / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  if (days > 0) {
    return `${days}d ${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
  }
  return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
}

export function paymentDueLabel(lot) {
  if (!lot || lot.paid || lot.status !== "SOLD" || !lot.paymentDueAt) {
    return null;
  }
  const remaining = Date.parse(lot.paymentDueAt) - Date.now();
  if (Number.isNaN(remaining)) {
    return null;
  }
  if (remaining <= 0) {
    return "Payment window closed";
  }
  return `Pay within ${remainingLabel(lot.paymentDueAt, "0s")}`;
}
