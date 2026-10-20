import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { ClaimsTable } from '../components/ClaimsTable';
import { Pager } from '../components/Pager';
import { TableSkeleton } from '../components/Skeleton';
import { sortParam, type SortState } from '../components/SortHeader';
import { money } from '../lib/format';

export function ApprovalsPage() {
  const { user } = useAuth();
  const [sort, setSort] = useState<SortState>({ field: 'submittedAt', dir: 'asc' }); // oldest first: they have waited longest
  const [page, setPage] = useState(0);

  const q = useQuery({
    queryKey: ['approvals', sortParam(sort), page],
    queryFn: () => api.pendingApprovals({ sort: sortParam(sort), page, size: 10 }),
    placeholderData: keepPreviousData,
    refetchInterval: 30_000,
  });

  const isFinance = user!.role === 'FINANCE';
  const pageTotal = q.data?.content.reduce((sum, c) => sum + c.totalAmount, 0) ?? 0;

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Approvals</h1>
          <p>{isFinance
            ? 'Claims approved by a manager that are waiting for finance to approve or pay.'
            : 'Claims from your team that are waiting for your decision. Oldest first.'}</p>
        </div>
      </div>

      {q.data && q.data.totalElements > 0 && (
        <div className="stat-grid">
          <div className="card stat"><div className="label">Waiting</div><div className="value">{q.data.totalElements}</div></div>
          <div className="card stat"><div className="label">Value on this page</div><div className="value num">{money(pageTotal)}</div></div>
        </div>
      )}

      <div className="card">
        {q.isPending && <TableSkeleton />}
        {q.isError && <div className="empty"><h3>Could not load the queue</h3><button className="btn" onClick={() => q.refetch()}>Try again</button></div>}
        {q.data && q.data.totalElements === 0 && (
          <div className="empty"><h3>You are all caught up</h3><p>Nothing is waiting for your decision right now.</p></div>
        )}
        {q.data && q.data.totalElements > 0 && (
          <>
            <ClaimsTable claims={q.data.content} sort={sort} onSort={(s) => { setSort(s); setPage(0); }} showClaimant />
            <Pager page={q.data} onPage={setPage} />
          </>
        )}
      </div>
    </>
  );
}
