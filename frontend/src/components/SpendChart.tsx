import { useEffect, useRef, useState } from 'react';
import type { Category } from '../api/types';
import { CATEGORIES } from '../api/types';
import { compactMoney, niceScale, type MonthColumn } from '../lib/chart';
import { CATEGORY_LABEL, monthLabel, money } from '../lib/format';
import { ChartTooltip } from './ChartTooltip';

// Colour follows the category, never its rank: filtering the report must not repaint what is left.
export const CATEGORY_COLOR: Record<Category, string> = {
  TRAVEL: 'var(--series-1)', FOOD: 'var(--series-2)', LODGING: 'var(--series-3)', OTHER: 'var(--series-4)',
};

const H = 260;
const M = { top: 22, right: 8, bottom: 28, left: 54 };
const MAX_BAR = 24;     // bars stay thin; the rest of the band is air
const GAP = 2;          // surface-coloured gap between stacked segments
const R = 4;            // rounded data end, square at the baseline

function useWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [w, setW] = useState(640);
  useEffect(() => {
    if (!ref.current) return;
    const el = ref.current;
    setW(el.clientWidth || 640);
    if (typeof ResizeObserver === 'undefined') return;
    const ro = new ResizeObserver(() => setW(el.clientWidth || 640));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);
  return [ref, w] as const;
}

/** Column whose top corners are rounded and whose bottom is square (it sits on the baseline). */
function roundedTop(x: number, y: number, w: number, h: number, r: number) {
  const rr = Math.min(r, h, w / 2);
  return `M${x},${y + h} L${x},${y + rr} Q${x},${y} ${x + rr},${y} L${x + w - rr},${y} Q${x + w},${y} ${x + w},${y + rr} L${x + w},${y + h} Z`;
}

interface Props {
  columns: MonthColumn[];
  /** 'single' = one series (total per month, slot 1); 'stacked' = split by category. */
  mode: 'single' | 'stacked';
}

export function MonthlySpendChart({ columns, mode }: Props) {
  const [wrapRef, width] = useWidth<HTMLDivElement>();
  const [hover, setHover] = useState<{ i: number; x: number } | null>(null);

  const plotW = Math.max(120, width - M.left - M.right - 40);
  const plotH = H - M.top - M.bottom;
  const { top, step } = niceScale(Math.max(0, ...columns.map((c) => c.total)));
  const y = (v: number) => M.top + plotH - (v / top) * plotH;
  const band = plotW / Math.max(1, columns.length);
  const barW = Math.min(MAX_BAR, band * 0.6);
  const ticks = Array.from({ length: Math.round(top / step) + 1 }, (_, i) => i * step);
  const peak = columns.reduce((best, c, i) => (c.total > (columns[best]?.total ?? -1) ? i : best), 0);
  const last = columns.length - 1;
  const labelEvery = Math.ceil(columns.length / Math.max(1, Math.floor(plotW / 64)));
  const usedCategories = CATEGORIES.filter((c) => columns.some((col) => (col.byCategory[c] ?? 0) > 0));

  const show = (i: number) => setHover({ i, x: 40 + M.left + band * i + band / 2 - 20 });
  const col = hover ? columns[hover.i] : null;

  return (
    <div className="viz">
      {mode === 'stacked' && usedCategories.length > 1 && (
        <div className="viz-legend" aria-label="Legend">
          {usedCategories.map((c) => <span key={c}><i style={{ background: CATEGORY_COLOR[c] }} />{CATEGORY_LABEL[c]}</span>)}
        </div>
      )}
      <div className="viz-wrap" ref={wrapRef} onPointerLeave={() => setHover(null)}>
        <svg width={width - 40} height={H} role="img"
          aria-label={`Spend by month, ${columns.length} months. Peak ${columns[peak] ? monthLabel(columns[peak].month) : ''} at ${money(columns[peak]?.total ?? 0)}. The table below has every value.`}>
          {ticks.map((t) => (
            <g key={t}>
              <line className={t === 0 ? 'axis-line' : 'grid-line'} x1={M.left} x2={M.left + plotW} y1={y(t)} y2={y(t)} />
              <text className="tick" x={M.left - 8} y={y(t) + 4} textAnchor="end">{compactMoney(t)}</text>
            </g>
          ))}

          {columns.map((c, i) => {
            const cx = M.left + band * i + band / 2;
            const x0 = cx - barW / 2;
            const segs = mode === 'stacked'
              ? usedCategories.map((cat) => ({ key: cat, v: c.byCategory[cat] ?? 0, color: CATEGORY_COLOR[cat] })).filter((s) => s.v > 0)
              : c.total > 0 ? [{ key: 'total', v: c.total, color: 'var(--series-1)' }] : [];
            let acc = 0;
            return (
              <g key={c.month}>
                {segs.map((s, k) => {
                  const yTop = y(acc + s.v);
                  const yBottom = y(acc);
                  acc += s.v;
                  const isTop = k === segs.length - 1;
                  const h = Math.max(1, yBottom - yTop - (k > 0 ? GAP : 0));
                  return isTop
                    ? <path key={s.key} d={roundedTop(x0, yTop, barW, h, R)} fill={s.color} className={hover && hover.i !== i ? 'dim' : undefined} />
                    : <rect key={s.key} x={x0} y={yTop + (k > 0 ? GAP : 0)} width={barW} height={h} fill={s.color} className={hover && hover.i !== i ? 'dim' : undefined} />;
                })}
                {(i === peak || (i === last && last !== peak)) && c.total > 0 && (
                  <text className="peak-label" x={cx} y={y(c.total) - 6} textAnchor="middle">{compactMoney(c.total)}</text>
                )}
                {(i % labelEvery === 0 || i === last) && (
                  <text className="tick" x={cx} y={H - 8} textAnchor="middle">{monthLabel(c.month)}</text>
                )}
                {/* hit area: the whole band, far bigger than the 24px bar, and keyboard focusable */}
                <rect className="col-hit" x={M.left + band * i} y={M.top} width={band} height={plotH}
                  tabIndex={0} role="img"
                  aria-label={`${monthLabel(c.month)}: ${money(c.total)}`}
                  onPointerMove={() => show(i)} onFocus={() => show(i)} onBlur={() => setHover(null)} />
              </g>
            );
          })}
        </svg>
        {hover && col && (
          <ChartTooltip x={hover.x + 20} y={y(col.total) + 8} title={monthLabel(col.month)}
            rows={mode === 'stacked'
              ? usedCategories.filter((c) => (col.byCategory[c] ?? 0) > 0).map((c) => ({ color: CATEGORY_COLOR[c], name: CATEGORY_LABEL[c], value: money(col.byCategory[c]!) }))
              : [{ color: 'var(--series-1)', name: 'Spend', value: money(col.total) }]}
            total={mode === 'stacked' ? money(col.total) : undefined} />
        )}
      </div>
    </div>
  );
}

/** One series (nominal categories): every bar the same colour, value at the tip, ranked by size. */
export function CategoryBars({ items }: { items: { category: Category; total: number }[] }) {
  const max = Math.max(1, ...items.map((i) => i.total));
  const [hover, setHover] = useState<Category | null>(null);
  return (
    <div className="viz hbars" aria-label="Spend by category" onPointerLeave={() => setHover(null)}>
      {items.map((it) => (
        <div key={it.category} className="hbar" tabIndex={0} aria-label={`${CATEGORY_LABEL[it.category]}: ${money(it.total)}`}
          onPointerEnter={() => setHover(it.category)} onFocus={() => setHover(it.category)} onBlur={() => setHover(null)}>
          <span className="muted">{CATEGORY_LABEL[it.category]}</span>
          <span className="track">
            <span className={`fill ${hover && hover !== it.category ? 'dim' : ''}`} style={{ display: 'block', width: `calc(${(it.total / max) * 100}% - 90px)` }} />
            <span className="val" style={{ left: `calc(${(it.total / max) * 100}% - 90px)` }}>{money(it.total)}</span>
          </span>
        </div>
      ))}
    </div>
  );
}
