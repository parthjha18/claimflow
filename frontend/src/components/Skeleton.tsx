export function TableSkeleton({ rows = 5, cols = 4 }: { rows?: number; cols?: number }) {
  return (
    <div className="card-pad" aria-busy="true" aria-label="Loading">
      {Array.from({ length: rows }, (_, r) => (
        <div key={r} style={{ display: 'grid', gridTemplateColumns: `2fr repeat(${cols - 1}, 1fr)`, gap: 20, padding: '10px 0' }}>
          {Array.from({ length: cols }, (_, c) => <div key={c} className="skeleton" />)}
        </div>
      ))}
    </div>
  );
}
