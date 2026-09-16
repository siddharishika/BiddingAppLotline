import { createContext, useCallback, useContext, useMemo, useState } from "react";

const ToastActionsContext = createContext(null);
const ToastListContext = createContext(null);

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const dismiss = useCallback((id) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const push = useCallback((type, message) => {
    if (!message) {
      return;
    }
    const id = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    setToasts((current) => [...current, { id, type, message }]);
    window.setTimeout(() => dismiss(id), 4500);
  }, [dismiss]);

  const actions = useMemo(() => ({
    error: (message) => push("error", message),
    success: (message) => push("success", message),
  }), [push]);

  const list = useMemo(() => ({ toasts, dismiss }), [toasts, dismiss]);

  return (
    <ToastActionsContext.Provider value={actions}>
      <ToastListContext.Provider value={list}>
        {children}
      </ToastListContext.Provider>
    </ToastActionsContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastActionsContext);
  if (!context) {
    throw new Error("useToast must be used within ToastProvider");
  }
  return context;
}

export function ToastViewport() {
  const list = useContext(ToastListContext);
  if (!list || list.toasts.length === 0) {
    return null;
  }
  return (
    <div className="toast-stack" role="status" aria-live="polite">
      {list.toasts.map((toast) => (
        <button
          key={toast.id}
          type="button"
          className={`toast toast-${toast.type}`}
          onClick={() => list.dismiss(toast.id)}
        >
          {toast.message}
        </button>
      ))}
    </div>
  );
}
