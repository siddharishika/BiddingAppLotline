import { useEffect, useState } from "react";
import { remainingLabel } from "../format";

export default function Timer({ endTime, endedLabel = "Closed", className = "" }) {
  const [label, setLabel] = useState(() => remainingLabel(endTime, endedLabel));

  useEffect(() => {
    setLabel(remainingLabel(endTime, endedLabel));
    const id = window.setInterval(() => setLabel(remainingLabel(endTime, endedLabel)), 1000);
    return () => window.clearInterval(id);
  }, [endTime, endedLabel]);

  return <time className={className}>{label}</time>;
}
