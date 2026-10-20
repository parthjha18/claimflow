import { describe, expect, it } from 'vitest';
import type { LineItem, Policy } from '../api/types';
import { checkPolicy, needsFinance } from './policyCheck';

const policy: Policy = {
  perClaimMax: 50000, receiptRequiredAbove: 1000, financeApprovalAbove: 10000, escalationAfterDays: 3, currency: 'INR',
  categoryLimits: { TRAVEL: 20000, FOOD: 5000, LODGING: 15000 },
};
let n = 0;
const item = (category: LineItem['category'], amount: number, receipt = true, description = 'x'): LineItem => ({
  id: `i${n++}`, category, amount, expenseDate: '2026-01-01', description, hasReceipt: receipt, receiptFilename: receipt ? 'r.pdf' : null,
});

describe('checkPolicy (mirrors the server PolicyValidator)', () => {
  it('accepts a compliant claim', () => {
    expect(checkPolicy(policy, [item('FOOD', 800, false), item('TRAVEL', 4000)])).toEqual([]);
  });

  it('asks for at least one item', () => {
    expect(checkPolicy(policy, [])).toHaveLength(1);
  });

  it('requires a receipt strictly above the threshold, not at it', () => {
    expect(checkPolicy(policy, [item('FOOD', 1000, false)])).toEqual([]);
    const issues = checkPolicy(policy, [item('FOOD', 1000.01, false, 'Dinner')]);
    expect(issues).toHaveLength(1);
    expect(issues[0].message).toContain('Dinner');
    expect(issues[0].itemId).toBeDefined();
  });

  it('applies category limits to the SUM of that category', () => {
    const six = Array.from({ length: 6 }, () => item('FOOD', 900, false));
    expect(checkPolicy(policy, six).map((i) => i.message)).toEqual([expect.stringContaining('Food total ₹5,400.00')]);
  });

  it('leaves categories without a limit unrestricted', () => {
    expect(checkPolicy(policy, [item('OTHER', 9000)])).toEqual([]);
  });

  it('enforces the per-claim maximum', () => {
    const issues = checkPolicy(policy, [item('OTHER', 30000), item('OTHER', 30000)]);
    expect(issues.map((i) => i.message)).toEqual([expect.stringContaining('per-claim maximum')]);
  });

  it('adds money in whole paise, so 0.1 + 0.2 style float errors cannot flip a boundary', () => {
    const tenThousandths = Array.from({ length: 3 }, () => item('FOOD', 1666.66));
    expect(checkPolicy({ ...policy, categoryLimits: { FOOD: 4999.98 } }, tenThousandths)).toEqual([]);
  });

  it('finance threshold is exclusive', () => {
    expect(needsFinance(policy, 10000)).toBe(false);
    expect(needsFinance(policy, 10000.01)).toBe(true);
  });
});
