import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { ApiError } from '../api/client';

interface ToastItem {
  id: number;
  kind: 'success' | 'error';
  title: string;
  details?: string[];
}

interface ToastApi {
  success: (message: string) => void;
  error: (err: unknown) => void;
}

const ToastContext = createContext<ToastApi | null>(null);
let nextId = 1;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);

  const push = useCallback((item: Omit<ToastItem, 'id'>) => {
    const id = nextId++;
    setItems((cur) => [...cur, { ...item, id }]);
    setTimeout(() => setItems((cur) => cur.filter((t) => t.id !== id)), item.kind === 'error' ? 7000 : 4000);
  }, []);

  const api = useMemo<ToastApi>(
    () => ({
      success: (message) => push({ kind: 'success', title: message }),
      error: (err) => {
        if (err instanceof ApiError) push({ kind: 'error', title: err.message, details: err.details });
        else push({ kind: 'error', title: err instanceof Error ? err.message : 'Something went wrong' });
      },
    }),
    [push],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toasts" role="status" aria-live="polite">
        {items.map((t) => (
          <div key={t.id} className={`toast ${t.kind}`}>
            <strong>{t.title}</strong>
            {t.details && t.details.length > 0 && (
              <ul>{t.details.map((d) => <li key={d}>{d}</li>)}</ul>
            )}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>');
  return ctx;
}
