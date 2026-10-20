import { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import type { ClaimStatus } from '../api/types';
import { ClaimsTable } from '../components/ClaimsTable';
import { Icon } from '../components/Icon';
import { Pager } from '../components/Pager';
import { TableSkeleton } from '../components/Skeleton';
import { sortParam, type SortState } from '../components/SortHeader';
import { STATUS_LABEL } from '../lib/format';

const STATUSES = Object.keys(STATUS_LABEL) as ClaimStatus[];

export function MyClaimsPage() {
  const [status, setStatus] = useState<ClaimStatus | ''>('');
  const [sort, setSort] = useState<SortState>({ field: 'createdAt', dir: 'desc' });
  const [page, setPage] = useState(0);

  const q = useQuery({
    queryKey: ['claims', 'mine', status, sortParam(sort), page],
    queryFn: () => api.myClaims({ status, sort: sortParam(sort), page, size: 10 }),
    placeholderData: keepPreviousData,
  });

  return (
    <>
      <div className="page-head">
        <div>
          <h1>My claims</h1>
          <p>Everything you have filed, and where it is in the approval chain.</p>
        </div>
        <Link to="/claims/new" className="btn primary"><Icon name="plus" size={16} /> New claim</Link>
      </div>

      <div className="card">
        <div className="toolbar">
          <label htmlFor="status" className="muted">Status</label>
          <select id="status" className="input" style={{ width: 'auto' }} value={status}
            onChange={(e) => { setStatus(e.target.value as ClaimStatus | ''); setPage(0); }}>
            <option value="">All</option>
            {STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
          </select>
        </div>

        {q.isPending && <TableSkeleton />}
        {q.isError && <div className="empty"><h3>Could not load your claims</h3><button className="btn" onClick={() => q.refetch()}>Try again</button></div>}
        {q.data && q.data.totalElements === 0 && (
          <div className="empty">
            <h3>{status ? 'No claims with this status' : 'No claims yet'}</h3>
            <p>{status ? 'Try another filter.' : 'Create your first claim and add the expenses to it.'}</p>
            {!status && <p style={{ marginTop: 14 }}><Link to="/claims/new" className="btn primary">New claim</Link></p>}
          </div>
        )}
        {q.data && q.data.totalElements > 0 && (
          <>
            <ClaimsTable claims={q.data.content} sort={sort} onSort={(s) => { setSort(s); setPage(0); }} />
            <Pager page={q.data} onPage={setPage} />
          </>
        )}
      </div>
    </>
  );
}
