import type { ReactNode } from 'react';

/** Positioned by the chart (x, y in the chart wrapper's pixels). Content is JSX text, never innerHTML. */
export function ChartTooltip({ x, y, title, rows, total }: {
  x: number; y: number; title: string; rows: { color: string; name: string; value: string }[]; total?: ReactNode;
}) {
  return (
    <div className="viz-tip" style={{ left: x, top: y }} role="tooltip">
      <div className="t-title">{title}</div>
      {rows.map((r) => (
        <div className="t-row" key={r.name}>
          <span className="k"><i style={{ background: r.color }} />{r.name}</span>
          <strong>{r.value}</strong>
        </div>
      ))}
      {total != null && <div className="t-row t-total"><span className="k">Total</span><strong>{total}</strong></div>}
    </div>
  );
}
