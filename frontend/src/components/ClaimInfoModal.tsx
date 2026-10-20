import { useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import { Modal } from './Modal';

interface Props {
  open: boolean;
  onClose: () => void;
  title: string;
  description: string | null;
  onSave: (title: string, description: string) => Promise<unknown>;
}

export function ClaimInfoModal({ open, onClose, title, description, onSave }: Props) {
  return (
    <Modal open={open} onClose={onClose} title="Edit claim details">
      {open && <InfoForm title={title} description={description} onSave={onSave} onClose={onClose} />}
    </Modal>
  );
}

function InfoForm({ title, description, onSave, onClose }: Omit<Props, 'open'>) {
  const [t, setT] = useState(title);
  const [d, setD] = useState(description ?? '');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await onSave(t.trim(), d.trim());
      onClose();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save');
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} style={{ marginTop: 14 }}>
      {error && <div className="form-error" role="alert">{error}</div>}
      <div className="field">
        <label htmlFor="t">Title</label>
        <input id="t" className="input" required maxLength={200} data-autofocus value={t} onChange={(e) => setT(e.target.value)} />
      </div>
      <div className="field">
        <label htmlFor="d">Description</label>
        <textarea id="d" className="input" maxLength={2000} value={d} onChange={(e) => setD(e.target.value)} />
      </div>
      <div className="actions" style={{ justifyContent: 'flex-end' }}>
        <button type="button" className="btn" onClick={onClose}>Cancel</button>
        <button className="btn primary" disabled={busy || !t.trim()}>{busy ? 'Saving…' : 'Save'}</button>
      </div>
    </form>
  );
}
