import { describe, expect, it } from 'vitest';
import type { SpendRow } from '../api/types';
import { compactMoney, monthsBetween, niceScale, toMonthColumns, totalsByCategory } from './chart';

const row = (month: string, category: SpendRow['category'], total: number): SpendRow => ({ month, category, total, itemCount: 1, claimCount: 1 });

describe('niceScale', () => {
  it('rounds the axis top up to a clean number with at most five intervals', () => {
    expect(niceScale(9300)).toEqual({ top: 10000, step: 2000 });
    expect(niceScale(1)).toEqual({ top: 1, step: 0.2 });
    expect(niceScale(48210)).toEqual({ top: 50000, step: 10000 });
  });
  it('never returns a top below the data', () => {
    for (const max of [0.5, 7, 99, 1234, 56789, 1e6 + 1]) expect(niceScale(max).top).toBeGreaterThanOrEqual(max);
  });
  it('has a sane default for empty data', () => {
    expect(niceScale(0).top).toBeGreaterThan(0);
  });
});

describe('compactMoney', () => {
  it('uses Indian units', () => {
    expect(compactMoney(0)).toBe('₹0');
    expect(compactMoney(950)).toBe('₹950');
    expect(compactMoney(2500)).toBe('₹2.5K');
    expect(compactMoney(150000)).toBe('₹1.5L');
    expect(compactMoney(25000000)).toBe('₹2.5Cr');
  });
});

describe('monthsBetween / toMonthColumns', () => {
  it('lists every month inclusive across a year boundary', () => {
    expect(monthsBetween('2025-11', '2026-02')).toEqual(['2025-11', '2025-12', '2026-01', '2026-02']);
  });

  it('pivots rows into one column per month and fills empty months with zero', () => {
    const cols = toMonthColumns(
      [row('2026-01', 'FOOD', 300), row('2026-01', 'TRAVEL', 700), row('2026-03', 'FOOD', 50)],
      '2026-01-01', '2026-03-31',
    );
    expect(cols.map((c) => [c.month, c.total])).toEqual([['2026-01', 1000], ['2026-02', 0], ['2026-03', 50]]);
    expect(cols[0].byCategory).toEqual({ FOOD: 300, TRAVEL: 700 });
  });

  it('ranks categories by spend', () => {
    expect(totalsByCategory([row('2026-01', 'FOOD', 10), row('2026-02', 'TRAVEL', 90), row('2026-02', 'FOOD', 20)]))
      .toEqual([{ category: 'TRAVEL', total: 90 }, { category: 'FOOD', total: 30 }]);
  });
});
