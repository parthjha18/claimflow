import { useMemo, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import type { ClaimStatus } from '../api/types';
import { CategoryBars, MonthlySpendChart } from '../components/SpendChart';
import { Pager } from '../components/Pager';
import { TableSkeleton } from '../components/Skeleton';
import { SortHeader, type SortState } from '../components/SortHeader';
import { toMonthColumns, totalsByCategory } from '../lib/chart';
import { CATEGORY_LABEL, monthLabel, money, todayIso } from '../lib/format';

type GroupBy = 'MONTH' | 'CATEGORY' | 'MONTH_CATEGORY';
type Preset = '3' | '6' | '12' | 'ytd' | 'custom';

const PAID_ONLY: ClaimStatus[] = ['PAID'];
const WITH_APPROVED: ClaimStatus[] = ['PAID', 'FINANCE_APPROVED', 'MANAGER_APPROVED'];

function rangeFor(preset: Preset, custom: { from: string; to: string }) {
  if (preset === 'custom') return custom;
  const to = new Date();
  const from = preset === 'ytd' ? new Date(to.getFullYear(), 0, 1) : new Date(to.getFullYear(), to.getMonth() - (Number(preset) - 1), 1);
  const iso = (d: Date) => d.toLocaleDateString('en-CA');
  return { from: iso(from), to: iso(to) };
}

export function ReportsPage() {
  const [preset, setPreset] = useState<Preset>('6');
  const [custom, setCustom] = useState({ from: rangeFor('6', { from: '', to: '' }).from, to: todayIso() });
  const [groupBy, setGroupBy] = useState<GroupBy>('MONTH_CATEGORY');
  const [includeApproved, setIncludeApproved] = useState(false);
  const [sort, setSort] = useState<SortState>({ field: 'month', dir: 'desc' });
  const [page, setPage] = useState(0);

  const { from, to } = rangeFor(preset, custom);
  const status = includeApproved ? WITH_APPROVED : PAID_ONLY;
  const validRange = from <= to;

  // the chart always needs month x category detail; the table follows the chosen grouping, sort and page
  const chartQ = useQuery({
    queryKey: ['reports', 'chart', from, to, status],
    queryFn: () => api.spend({ from, to, status, groupBy: 'MONTH_CATEGORY', sort: 'month', dir: 'asc', size: 100 }),
    placeholderData: keepPreviousData,
    enabled: validRange,
  });
  const tableQ = useQuery({
    queryKey: ['reports', 'table', from, to, status, groupBy, sort.field, sort.dir, page],
    queryFn: () => api.spend({ from, to, status, groupBy, sort: sort.field, dir: sort.dir, page, size: 10 }),
    placeholderData: keepPreviousData,
    enabled: validRange,
  });

  const rows = chartQ.data?.rows.content ?? [];
  const columns = useMemo(() => toMonthColumns(rows, from, to), [rows, from, to]);
  const categories = useMemo(() => totalsByCategory(rows), [rows]);
  const peak = columns.reduce((b, c) => (c.total > b.total ? c : b), { month: '', total: 0, byCategory: {} });
  const grand = tableQ.data?.grandTotal ?? chartQ.data?.grandTotal ?? 0;
  const top = categories[0];
  const refetching = chartQ.isPlaceholderData || tableQ.isPlaceholderData;

  function changeGroup(g: GroupBy) {
    setGroupBy(g);
    setPage(0);
    setSort(g === 'CATEGORY' ? { field: 'total', dir: 'desc' } : { field: 'month', dir: 'desc' });
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Spend report</h1>
          <p>Where the money went, by month and category. Dates are the expense dates on each line item.</p>
        </div>
      </div>

      <div className="filters">
        <div className="field">
          <label htmlFor="range">Period</label>
          <select id="range" className="input" value={preset} onChange={(e) => { setPreset(e.target.value as Preset); setPage(0); }}>
            <option value="3">Last 3 months</option>
            <option value="6">Last 6 months</option>
            <option value="12">Last 12 months</option>
            <option value="ytd">This year</option>
            <option value="custom">Custom range</option>
          </select>
        </div>
        {preset === 'custom' && (
          <>
            <div className="field"><label htmlFor="from">From</label>
              <input id="from" type="date" className="input" value={custom.from} max={custom.to} onChange={(e) => setCustom((c) => ({ ...c, from: e.target.value }))} /></div>
            <div className="field"><label htmlFor="to">To</label>
              <input id="to" type="date" className="input" value={custom.to} min={custom.from} onChange={(e) => setCustom((c) => ({ ...c, to: e.target.value }))} /></div>
          </>
        )}
        <div className="field">
          <label htmlFor="group">Group by</label>
          <select id="group" className="input" value={groupBy} onChange={(e) => changeGroup(e.target.value as GroupBy)}>
            <option value="MONTH_CATEGORY">Month and category</option>
            <option value="MONTH">Month</option>
            <option value="CATEGORY">Category</option>
          </select>
        </div>
        <label className="check"><input type="checkbox" checked={includeApproved} onChange={(e) => { setIncludeApproved(e.target.checked); setPage(0); }} />
          Include approved, not yet paid</label>
      </div>

      {!validRange && <div className="banner warn">The start date is after the end date.</div>}

      {validRange && (
        <div style={{ opacity: refetching ? 0.6 : 1, transition: 'opacity 0.15s' }} className="stack">
          <div className="stat-grid" style={{ marginBottom: 0 }}>
            <div className="card stat"><div className="label">{includeApproved ? 'Paid and approved' : 'Paid out'}</div><div className="value num">{money(grand)}</div></div>
            <div className="card stat">
              <div className="label">Biggest category</div>
              <div className="value">{top ? CATEGORY_LABEL[top.category] : '-'}</div>
              {top && grand > 0 && <div className="muted num">{money(top.total)} · {Math.round((top.total / grand) * 100)}%</div>}
            </div>
            <div className="card stat">
              <div className="label">Peak month</div>
              <div className="value">{peak.total > 0 ? monthLabel(peak.month) : '-'}</div>
              {peak.total > 0 && <div className="muted num">{money(peak.total)}</div>}
            </div>
          </div>

          <div className="card">
            <div className="card-head">
              <h2>{groupBy === 'CATEGORY' ? 'Spend by category' : groupBy === 'MONTH' ? 'Spend by month' : 'Spend by month and category'}</h2>
            </div>
            {chartQ.isPending ? <TableSkeleton rows={4} cols={3} />
              : grand === 0 ? <div className="empty"><h3>No spend in this period</h3><p>Try a longer period, or include approved claims that are not paid yet.</p></div>
              : groupBy === 'CATEGORY' ? <CategoryBars items={categories} />
              : <MonthlySpendChart columns={columns} mode={groupBy === 'MONTH' ? 'single' : 'stacked'} />}
            {chartQ.data && chartQ.data.rows.totalElements > 100 && (
              <p className="muted" style={{ padding: '0 20px 14px', fontSize: '0.85rem' }}>The chart shows the first 100 groups; narrow the period to see the rest.</p>
            )}
          </div>

          <div className="card">
            <div className="card-head"><h2>Table</h2><span className="muted">Every value in the chart, sortable</span></div>
            {tableQ.isPending ? <TableSkeleton cols={5} /> : tableQ.data && tableQ.data.rows.totalElements === 0 ? (
              <div className="empty"><p>Nothing to show.</p></div>
            ) : tableQ.data && (
              <>
                <div className="table-wrap">
                  <table className="table">
                    <thead>
                      <tr>
                        {groupBy !== 'CATEGORY' && <SortHeader label="Month" field="month" sort={sort} onSort={(s) => { setSort(s); setPage(0); }} />}
                        {groupBy !== 'MONTH' && <SortHeader label="Category" field="category" sort={sort} onSort={(s) => { setSort(s); setPage(0); }} />}
                        <SortHeader label="Total" field="total" className="right" sort={sort} onSort={(s) => { setSort(s); setPage(0); }} />
                        <SortHeader label="Items" field="itemCount" className="right" sort={sort} onSort={(s) => { setSort(s); setPage(0); }} />
                        <SortHeader label="Claims" field="claimCount" className="right" sort={sort} onSort={(s) => { setSort(s); setPage(0); }} />
                      </tr>
                    </thead>
                    <tbody>
                      {tableQ.data.rows.content.map((r) => (
                        <tr key={`${r.month}-${r.category}`}>
                          {groupBy !== 'CATEGORY' && <td>{r.month ? monthLabel(r.month) : '-'}</td>}
                          {groupBy !== 'MONTH' && <td>{r.category ? CATEGORY_LABEL[r.category] : '-'}</td>}
                          <td className="right num">{money(r.total)}</td>
                          <td className="right num">{r.itemCount}</td>
                          <td className="right num">{r.claimCount}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <Pager page={tableQ.data.rows} onPage={setPage} />
              </>
            )}
          </div>
        </div>
      )}
    </>
  );
}
