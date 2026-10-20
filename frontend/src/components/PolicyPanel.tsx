import type { Policy } from '../api/types';
import type { PolicyIssue } from '../lib/policyCheck';
import { money } from '../lib/format';

export function PolicyPanel({ issues, policy, total, financeNeeded, approverName }: {
  issues: PolicyIssue[]; policy: Policy; total: number; financeNeeded: boolean; approverName: string | null;
}) {
  return (
    <div className="card card-pad">
      <h2 style={{ marginBottom: 10 }}>Before you submit</h2>
      {issues.length === 0 ? (
        <p className="ok-note">All policy checks pass.</p>
      ) : (
        <ul className="issue-list">{issues.map((i) => <li key={i.message}>{i.message}</li>)}</ul>
      )}
      <dl className="kv" style={{ marginTop: 14, fontSize: '0.9rem' }}>
        <dt>Total</dt><dd className="num">{money(total)}</dd>
        <dt>Route</dt>
        <dd>{approverName ? `${approverName} (manager)` : 'Your manager'}{financeNeeded ? ', then finance' : ', then payment'}</dd>
        {financeNeeded && <><dt>Why finance</dt><dd className="muted">Over {money(policy.financeApprovalAbove)}</dd></>}
      </dl>
    </div>
  );
}
