export interface SortState { field: string; dir: 'asc' | 'desc' }

export const sortParam = (s: SortState) => `${s.field},${s.dir}`;

export function SortHeader({ label, field, sort, onSort, className }: {
  label: string; field: string; sort: SortState; onSort: (s: SortState) => void; className?: string;
}) {
  const active = sort.field === field;
  return (
    <th
      className={`sortable ${className ?? ''}`}
      aria-sort={active ? (sort.dir === 'asc' ? 'ascending' : 'descending') : 'none'}
      onClick={() => onSort({ field, dir: active && sort.dir === 'desc' ? 'asc' : 'desc' })}
    >
      {label} {active ? (sort.dir === 'asc' ? '↑' : '↓') : ''}
    </th>
  );
}
