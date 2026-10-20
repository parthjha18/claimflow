import type { Category, SpendRow } from '../api/types';

/**
 * Picks the smallest "clean" step (1, 2, 2.5, 5 x 10^n) that covers the data in at most five intervals, and
 * rounds the axis top up to it, so the plot is tight without ugly tick values.
 */
export function niceScale(max: number): { top: number; step: number } {
  if (!(max > 0)) return { top: 1000, step: 250 };
  const pow = 10 ** Math.floor(Math.log10(max / 5));
  for (const m of [1, 2, 2.5, 5, 10]) {
    const step = Number((m * pow).toPrecision(12));
    const intervals = Math.ceil(max / step - 1e-9);
    if (intervals <= 5) return { top: Number((intervals * step).toPrecision(12)), step };
  }
  return { top: max, step: max / 4 };
}

const trim = (n: number) => String(Number(n.toFixed(2)));

/** Compact rupee amounts for axis ticks and labels: 0, ₹2.5K, ₹1.2L (lakh), ₹3Cr (crore). */
export function compactMoney(n: number): string {
  const abs = Math.abs(n);
  if (abs >= 1e7) return `₹${trim(n / 1e7)}Cr`;
  if (abs >= 1e5) return `₹${trim(n / 1e5)}L`;
  if (abs >= 1e3) return `₹${trim(n / 1e3)}K`;
  return `₹${trim(n)}`;
}

/** Every YYYY-MM from the month of `from` to the month of `to`, inclusive (so empty months still get a column). */
export function monthsBetween(from: string, to: string): string[] {
  const [fy, fm] = from.split('-').map(Number);
  const [ty, tm] = to.split('-').map(Number);
  const out: string[] = [];
  for (let y = fy, m = fm; y < ty || (y === ty && m <= tm); m++) {
    if (m > 12) { m = 1; y++; }
    out.push(`${y}-${String(m).padStart(2, '0')}`);
  }
  return out;
}

export interface MonthColumn {
  month: string;
  total: number;
  byCategory: Partial<Record<Category, number>>;
}

/** Pivots month x category rows into one column per month, filling gaps with zero. */
export function toMonthColumns(rows: SpendRow[], from: string, to: string): MonthColumn[] {
  const cols = new Map<string, MonthColumn>(
    monthsBetween(from.slice(0, 7), to.slice(0, 7)).map((m) => [m, { month: m, total: 0, byCategory: {} }]),
  );
  for (const r of rows) {
    if (!r.month) continue;
    const col = cols.get(r.month) ?? { month: r.month, total: 0, byCategory: {} };
    col.total += r.total;
    if (r.category) col.byCategory[r.category] = (col.byCategory[r.category] ?? 0) + r.total;
    cols.set(r.month, col);
  }
  return [...cols.values()].sort((a, b) => a.month.localeCompare(b.month));
}

export function totalsByCategory(rows: SpendRow[]): { category: Category; total: number }[] {
  const sums = new Map<Category, number>();
  for (const r of rows) if (r.category) sums.set(r.category, (sums.get(r.category) ?? 0) + r.total);
  return [...sums].map(([category, total]) => ({ category, total })).sort((a, b) => b.total - a.total);
}
