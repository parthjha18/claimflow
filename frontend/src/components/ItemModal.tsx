import { useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import { CATEGORIES, type LineItem, type LineItemInput, type Policy } from '../api/types';
import { CATEGORY_LABEL, money, todayIso } from '../lib/format';
import { Modal } from './Modal';

interface Props {
  open: boolean;
  onClose: () => void;
  item: LineItem | null;               // null = adding
  policy?: Policy;
  onSave: (input: LineItemInput) => Promise<unknown>;
}

export function ItemModal({ open, onClose, item, policy, onSave }: Props) {
  // remount the form each time the dialog opens so it starts from the right item
  return (
    <Modal open={open} onClose={onClose} title={item ? 'Edit line item' : 'Add line item'}>
      {open && <ItemForm key={item?.id ?? 'new'} item={item} policy={policy} onSave={onSave} onClose={onClose} />}
    </Modal>
  );
}

function ItemForm({ item, policy, onSave, onClose }: Omit<Props, 'open'>) {
  const [category, setCategory] = useState(item?.category ?? 'TRAVEL');
  const [amount, setAmount] = useState(item ? String(item.amount) : '');
  const [expenseDate, setExpenseDate] = useState(item?.expenseDate ?? todayIso());
  const [description, setDescription] = useState(item?.description ?? '');
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);

  const limit = policy?.categoryLimits[category];
  const amountNum = Number(amount);
  const needsReceipt = policy && amountNum > policy.receiptRequiredAbove;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await onSave({ category, amount: amountNum, expenseDate, description: description.trim() || undefined });
      onClose();
    } catch (err) {
      setError(err instanceof ApiError ? err : new ApiError(0, 'ERROR', 'Could not save the item'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} style={{ marginTop: 14 }}>
      {error && (
        <div className="form-error" role="alert">
          {error.message}
          {error.details.length > 0 && <ul>{error.details.map((d) => <li key={d}>{d}</li>)}</ul>}
        </div>
      )}
      <div className="row">
        <div className="field">
          <label htmlFor="category">Category</label>
          <select id="category" className="input" value={category} onChange={(e) => setCategory(e.target.value as typeof category)}>
            {CATEGORIES.map((c) => <option key={c} value={c}>{CATEGORY_LABEL[c]}</option>)}
          </select>
          {limit != null && <span className="hint">Limit for this category: {money(limit)} per claim</span>}
        </div>
        <div className="field">
          <label htmlFor="amount">Amount (INR)</label>
          <input id="amount" className="input num" type="number" inputMode="decimal" min="0.01" step="0.01" required data-autofocus
            value={amount} onChange={(e) => setAmount(e.target.value)} />
          {needsReceipt && <span className="hint" style={{ color: 'var(--s-submitted-fg)' }}>Needs a receipt (above {money(policy.receiptRequiredAbove)})</span>}
        </div>
      </div>
      <div className="field">
        <label htmlFor="date">Date of expense</label>
        <input id="date" className="input" type="date" required max={todayIso()} value={expenseDate}
          onChange={(e) => setExpenseDate(e.target.value)} />
      </div>
      <div className="field">
        <label htmlFor="desc">Description <span className="muted">(optional)</span></label>
        <input id="desc" className="input" maxLength={500} placeholder="e.g. Flight BLR to PNQ"
          value={description} onChange={(e) => setDescription(e.target.value)} />
      </div>
      <div className="actions" style={{ justifyContent: 'flex-end' }}>
        <button type="button" className="btn" onClick={onClose}>Cancel</button>
        <button className="btn primary" disabled={busy || !amount}>{busy ? 'Saving…' : item ? 'Save changes' : 'Add item'}</button>
      </div>
    </form>
  );
}
