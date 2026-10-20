import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { MonthColumn } from '../lib/chart';
import { CategoryBars, MonthlySpendChart } from './SpendChart';

const columns: MonthColumn[] = [
  { month: '2026-01', total: 1000, byCategory: { TRAVEL: 700, FOOD: 300 } },
  { month: '2026-02', total: 0, byCategory: {} },
  { month: '2026-03', total: 2500, byCategory: { TRAVEL: 2000, LODGING: 500 } },
];

describe('MonthlySpendChart', () => {
  it('shows a legend for stacked series, and only for categories that appear', () => {
    render(<MonthlySpendChart columns={columns} mode="stacked" />);
    const legend = screen.getByLabelText('Legend');
    expect(legend).toHaveTextContent('Travel');
    expect(legend).toHaveTextContent('Food');
    expect(legend).toHaveTextContent('Lodging');
    expect(legend).not.toHaveTextContent('Other');
  });

  it('has no legend box for a single series', () => {
    render(<MonthlySpendChart columns={columns} mode="single" />);
    expect(screen.queryByLabelText('Legend')).toBeNull();
  });

  it('gives every month a focusable, labelled hit area and shows the same tooltip on focus as on hover', async () => {
    const user = userEvent.setup();
    render(<MonthlySpendChart columns={columns} mode="stacked" />);
    const mar = screen.getByRole('img', { name: /Mar 2026: ₹2,500.00/ });
    expect(mar).toHaveAttribute('tabindex', '0');
    await user.tab();                       // first month
    await user.tab(); await user.tab();     // third month
    expect(mar).toHaveFocus();
    const tip = await screen.findByRole('tooltip');
    expect(tip).toHaveTextContent('Travel');
    expect(tip).toHaveTextContent('₹2,000.00');
    expect(tip).toHaveTextContent('Total');
  });

  it('describes the chart for screen readers and points to the table', () => {
    render(<MonthlySpendChart columns={columns} mode="single" />);
    expect(screen.getAllByRole('img')[0]).toHaveAttribute('aria-label', expect.stringContaining('The table below has every value'));
  });
});

describe('CategoryBars', () => {
  it('lists categories with the value at the tip', () => {
    render(<CategoryBars items={[{ category: 'TRAVEL', total: 900 }, { category: 'FOOD', total: 100 }]} />);
    expect(screen.getByLabelText('Travel: ₹900.00')).toBeInTheDocument();
    expect(screen.getByText('₹100.00')).toBeInTheDocument();
  });
});
