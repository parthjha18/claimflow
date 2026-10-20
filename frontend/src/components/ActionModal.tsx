import { useRef, useState } from 'react';
import { ApiError, newIdempotencyKey } from '../api/client';
import type { WorkflowAction } from '../lib/workflow';
import { Modal } from './Modal';

export const ACTION_COPY: Record<WorkflowAction, {
  title: string; confirm: string; commentLabel: string; required: boolean; danger?: boolean; blurb: string;
}> = {
  approve: { title: 'Approve claim', confirm: 'Approve', commentLabel: 'Comment (optional)', required: false, blurb: 'The claim moves to the next stage.' },
  pay: { title: 'Mark as paid', confirm: 'Mark as paid', commentLabel: 'Payment reference (optional)', required: false, blurb: 'This closes the claim. It cannot be undone.' },
  requestChanges: { title: 'Request changes', confirm: 'Send back', commentLabel: 'What needs to change?', required: true, blurb: 'The claim returns to draft so the employee can fix it and resubmit.' },
  reject: { title: 'Reject claim', confirm: 'Reject claim', commentLabel: 'Reason for rejecting', required: true, danger: true, blurb: 'Rejection is final. The employee would have to file a new claim.' },
};

interface Props {
  action: WorkflowAction | null;
  claimTitle: string;
  onClose: () => void;
  /** Receives the comment and the Idempotency-Key for this attempt. */
  onConfirm: (action: WorkflowAction, comment: string, key: string) => Promise<unknown>;
}

export function ActionModal({ action, claimTitle, onClose, onConfirm }: Props) {
  return (
    <Modal open={action !== null} onClose={onClose} title={action ? ACTION_COPY[action].title : ''}>
      {action && <ActionForm key={action} action={action} claimTitle={claimTitle} onClose={onClose} onConfirm={onConfirm} />}
    </Modal>
  );
}

function ActionForm({ action, claimTitle, onClose, onConfirm }: Omit<Props, 'action'> & { action: WorkflowAction }) {
  const copy = ACTION_COPY[action];
  const [comment, setComment] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // One key per opened dialog: if the request times out and the user clicks again, the server sees the same
  // key and returns the first result instead of acting twice. A new dialog is a new decision, so a new key.
  const key = useRef(newIdempotencyKey());

  async function confirm() {
    setBusy(true);
    setError(null);
    try {
      await onConfirm(action, comment.trim(), key.current);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not reach the server. You can safely try again.');
      setBusy(false);
      return;
    }
    onClose();
  }

  const missing = copy.required && !comment.trim();
  return (
    <>
      <p className="muted" style={{ marginBottom: 14 }}><strong style={{ color: 'var(--text)' }}>{claimTitle}</strong><br />{copy.blurb}</p>
      {error && <div className="form-error" role="alert">{error}</div>}
      <div className="field">
        <label htmlFor="comment">{copy.commentLabel}</label>
        <textarea id="comment" className="input" maxLength={1000} data-autofocus value={comment} onChange={(e) => setComment(e.target.value)} />
      </div>
      <div className="actions" style={{ justifyContent: 'flex-end' }}>
        <button className="btn" onClick={onClose} disabled={busy}>Cancel</button>
        <button className={`btn ${copy.danger ? 'danger' : 'primary'}`} onClick={confirm} disabled={busy || missing}>
          {busy ? 'Working…' : copy.confirm}
        </button>
      </div>
    </>
  );
}
