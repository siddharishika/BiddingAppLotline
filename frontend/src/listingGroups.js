export function canWithdraw(lot) {
  return lot.status === "SCHEDULED" || lot.status === "ENDED";
}

export function canWithdrawCollection(lots) {
  const open = (lots || []).filter((lot) => !isWithdrawn(lot));
  return open.length > 0 && open.every(canWithdraw);
}

export function isWithdrawn(lot) {
  return lot.status === "CANCELLED";
}

export function isUnsold(lot) {
  return lot.status === "ENDED";
}

export function groupListedLots(lots) {
  const collections = new Map();
  const collectionOrder = [];
  const singles = [];
  for (const lot of lots) {
    if (lot.collectionId && lot.collectionName) {
      let group = collections.get(lot.collectionId);
      if (!group) {
        group = {
          kind: "COLLECTION",
          collectionId: lot.collectionId,
          collectionName: lot.collectionName,
          lots: [],
        };
        collections.set(lot.collectionId, group);
        collectionOrder.push(group);
      }
      group.lots.push(lot);
    } else {
      singles.push(lot);
    }
  }
  const named = [];
  for (const group of collectionOrder) {
    if (group.lots.length < 2) {
      singles.push(...group.lots);
    } else {
      named.push(group);
    }
  }
  return { collections: named, singles };
}

export function splitWithdrawn(items, isItemWithdrawn) {
  const active = [];
  const withdrawn = [];
  for (const item of items) {
    if (isItemWithdrawn(item)) {
      withdrawn.push(item);
    } else {
      active.push(item);
    }
  }
  return { active, withdrawn };
}

export function splitConsignorSingles(lots) {
  const active = [];
  const unsold = [];
  const withdrawn = [];
  for (const lot of lots) {
    if (isWithdrawn(lot)) {
      withdrawn.push(lot);
    } else if (isUnsold(lot)) {
      unsold.push(lot);
    } else {
      active.push(lot);
    }
  }
  return { active, unsold, withdrawn };
}
