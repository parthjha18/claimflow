import type { Page } from '../api/types';

export function Pager({ page, onPage }: { page: Page<unknown>; onPage: (p: number) => void }) {
  if (page.totalElements === 0) return null;
  const from = page.page * page.size + 1;
  const to = Math.min(from + page.content.length - 1, page.totalElements);
  return (
    <div className="pager">
      <span>{from}-{to} of {page.totalElements}</span>
      <span className="actions">
        <button className="btn sm" disabled={page.page <= 0} onClick={() => onPage(page.page - 1)}>Previous</button>
        <button className="btn sm" disabled={page.page + 1 >= page.totalPages} onClick={() => onPage(page.page + 1)}>Next</button>
      </span>
    </div>
  );
}
