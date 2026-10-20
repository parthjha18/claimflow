import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import { ApiError } from '../api/client';
import { CATEGORIES, type Category, type Policy } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Icon } from '../components/Icon';
import { TableSkeleton } from '../components/Skeleton';
import { useToast } from '../components/Toast';
import { CATEGORY_LABEL, money } from '../lib/format';

export function PolicyPage() {
  const { user } = useAuth();
  const isAdmin = user!.role === 'ADMIN';
  const [editing, setEditing] = useState(false);
  const q = useQuery({ queryKey: ['policy'], queryFn: api.policy });

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Expense policy</h1>
          <p>The rules every claim is checked against when it is submitted.</p>
        </div>
        {isAdmin && q.data && !editing && (
          <button className="btn primary" onClick={() => setEditing(true)}><Icon name="edit" size={16} /> Edit policy</button>
        )}
      </div>

      {q.isPending && <div className="card"><TableSkeleton rows={4} cols={2} /></div>}
      {q.isError && <div className="card empty"><h3>No policy found</h3><p>An admin needs to create the organisation's expense policy before claims can be submitted.</p></div>}
      {q.data && (editing
        ? <PolicyForm policy={q.data} onDone={() => setEditing(false)} />
        : <PolicyView policy={q.data} />)}
    </>
  );
}

function PolicyView({ policy }: { policy: Policy }) {
  return (
    <div className="stack">
      <div className="stat-grid" style={{ marginBottom: 0 }}>
        <div className="card stat"><div className="label">Per-claim maximum</div><div className="value num">{money(policy.perClaimMax)}</div></div>
        <div className="card stat"><div className="label">Receipt required above</div><div className="value num">{money(policy.receiptRequiredAbove)}</div></div>
        <div className="card stat"><div className="label">Finance approval above</div><div className="value num">{money(policy.financeApprovalAbove)}</div></div>
        <div className="card stat"><div className="label">Escalate after</div><div className="value">{policy.escalationAfterDays} day{policy.escalationAfterDays === 1 ? '' : 's'}</div></div>
      </div>
      <div className="card">
        <div className="card-head"><h2>Category limits</h2><span className="muted">Maximum total per category on one claim</span></div>
        <table className="table">
          <tbody>
            {CATEGORIES.map((c) => (
              <tr key={c}>
                <td className="title-cell">{CATEGORY_LABEL[c]}</td>
                <td className="right num">{policy.categoryLimits[c] != null ? money(policy.categoryLimits[c]) : <span className="muted">No limit</span>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="muted" style={{ fontSize: '0.88rem' }}>
        Claims above the finance threshold need a finance approval after the manager; smaller ones go from the manager straight to payment.
        A claim waiting on its manager for longer than the escalation period is passed to that manager's manager.
      </p>
    </div>
  );
}

function PolicyForm({ policy, onDone }: { policy: Policy; onDone: () => void }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [v, setV] = useState({
    perClaimMax: String(policy.perClaimMax),
    receiptRequiredAbove: String(policy.receiptRequiredAbove),
    financeApprovalAbove: String(policy.financeApprovalAbove),
    escalationAfterDays: String(policy.escalationAfterDays),
  });
  const [limits, setLimits] = useState<Record<Category, string>>(
    () => Object.fromEntries(CATEGORIES.map((c) => [c, policy.categoryLimits[c] != null ? String(policy.categoryLimits[c]) : ''])) as Record<Category, string>,
  );

  const save = useMutation({
    mutationFn: () => api.savePolicy({
      perClaimMax: Number(v.perClaimMax),
      receiptRequiredAbove: Number(v.receiptRequiredAbove),
      financeApprovalAbove: Number(v.financeApprovalAbove),
      escalationAfterDays: Number(v.escalationAfterDays),
      categoryLimits: Object.fromEntries(CATEGORIES.filter((c) => limits[c] !== '').map((c) => [c, Number(limits[c])])),
    }),
    onSuccess: (saved) => {
      queryClient.setQueryData(['policy'], saved);
      toast.success('Policy saved. It applies to claims submitted from now on.');
      onDone();
    },
  });

  const num = (key: keyof typeof v, label: string, hint?: string, step = '0.01', min = '0') => (
    <div className="field">
      <label htmlFor={key}>{label}</label>
      <input id={key} className="input num" type="number" step={step} min={min} required value={v[key]}
        onChange={(e) => setV((cur) => ({ ...cur, [key]: e.target.value }))} />
      {hint && <span className="hint">{hint}</span>}
    </div>
  );

  const err = save.error instanceof ApiError ? save.error : null;
  return (
    <form className="card card-pad" style={{ maxWidth: 720 }} onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate(); }}>
      {err && <div className="form-error" role="alert">{err.message}{err.details.length > 0 && <ul>{err.details.map((d) => <li key={d}>{d}</li>)}</ul>}</div>}
      <div className="row">
        {num('perClaimMax', 'Per-claim maximum (INR)', 'No claim may total more than this.', '0.01', '0.01')}
        {num('receiptRequiredAbove', 'Receipt required above (INR)', 'Items above this need a receipt.')}
      </div>
      <div className="row">
        {num('financeApprovalAbove', 'Finance approval above (INR)', 'Claims above this need finance after the manager.')}
        {num('escalationAfterDays', 'Escalate after (days)', 'Unanswered claims move to the next manager.', '1', '1')}
      </div>
      <h2 style={{ margin: '8px 0 12px' }}>Category limits</h2>
      <div className="row">
        {CATEGORIES.map((c) => (
          <div className="field" key={c}>
            <label htmlFor={`limit-${c}`}>{CATEGORY_LABEL[c]}</label>
            <input id={`limit-${c}`} className="input num" type="number" step="0.01" min="0.01" placeholder="No limit"
              value={limits[c]} onChange={(e) => setLimits((cur) => ({ ...cur, [c]: e.target.value }))} />
          </div>
        ))}
      </div>
      <div className="actions">
        <button className="btn primary" disabled={save.isPending}>{save.isPending ? 'Saving…' : 'Save policy'}</button>
        <button type="button" className="btn" onClick={onDone}>Cancel</button>
      </div>
    </form>
  );
}
