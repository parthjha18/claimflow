import { useNavigate } from 'react-router-dom';
import type { ClaimSummary } from '../api/types';
import { date, money } from '../lib/format';
import { SortHeader, type SortState } from './SortHeader';
import { StatusBadge } from './StatusBadge';

interface Props {
  claims: ClaimSummary[];
  sort?: SortState;
  onSort?: (s: SortState) => void;
  /** Show who filed the claim (approval queues) instead of who is approving it. */
  showClaimant?: boolean;
}

export function ClaimsTable({ claims, sort, onSort, showClaimant }: Props) {
  const navigate = useNavigate();
  const head = (label: string, field: string, className?: string) =>
    sort && onSort
      ? <SortHeader label={label} field={field} sort={sort} onSort={onSort} className={className} />
      : <th className={className}>{label}</th>;

  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            {head('Claim', 'title')}
            {head('Amount', 'totalAmount', 'right')}
            {head('Status', 'status')}
            {head(showClaimant ? 'Submitted' : 'Created', showClaimant ? 'submittedAt' : 'createdAt')}
          </tr>
        </thead>
        <tbody>
          {claims.map((c) => (
            <tr key={c.id} className="clickable" onClick={() => navigate(`/claims/${c.id}`)}>
              <td>
                <div className="title-cell">
                  <a href={`/claims/${c.id}`} onClick={(e) => { e.preventDefault(); navigate(`/claims/${c.id}`); }}
                    style={{ color: 'inherit' }}>{c.title}</a>
                </div>
                <div className="sub">
                  {showClaimant ? `by ${c.claimantName}` : c.status === 'SUBMITTED' && c.currentApproverName ? `with ${c.currentApproverName}` : ' '}
                </div>
              </td>
              <td className="right num">{money(c.totalAmount)}</td>
              <td>
                <StatusBadge status={c.status} />
                {c.requiresFinance && c.status !== 'DRAFT' && c.status !== 'PAID' && c.status !== 'REJECTED' && (
                  <div className="sub" style={{ marginTop: 3 }}>needs finance</div>
                )}
              </td>
              <td className="muted nowrap">{date(showClaimant ? c.submittedAt : c.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
