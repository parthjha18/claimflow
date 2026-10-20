import { Modal } from './Modal';

export function ConfirmModal({ open, title, message, confirmLabel, busy, onConfirm, onClose }: {
  open: boolean; title: string; message: string; confirmLabel: string; busy?: boolean;
  onConfirm: () => void; onClose: () => void;
}) {
  return (
    <Modal open={open} onClose={onClose} title={title} description={message}
      footer={<>
        <button className="btn" onClick={onClose}>Cancel</button>
        <button className="btn danger" onClick={onConfirm} disabled={busy}>{busy ? 'Working…' : confirmLabel}</button>
      </>} />
  );
}
