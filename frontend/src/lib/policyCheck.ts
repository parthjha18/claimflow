import type { Category, LineItem, Policy } from '../api/types';
import { CATEGORY_LABEL, money } from './format';

const cents = (n: number) => Math.round(n * 100);

export interface PolicyIssue {
  message: string;
  /** Item the issue is about, so the table can flag the row. */
  itemId?: string;
}

/**
 * Pre-flight copy of the server's PolicyValidator, so people see problems while editing instead of on submit.
 * The server stays the authority: it re-checks everything when the claim is submitted.
 */
export function checkPolicy(policy: Policy, items: LineItem[]): PolicyIssue[] {
  if (items.length === 0) return [{ message: 'Add at least one line item.' }];

  const issues: PolicyIssue[] = [];
  const total = items.reduce((sum, i) => sum + cents(i.amount), 0);
  if (total > cents(policy.perClaimMax)) {
    issues.push({ message: `Claim total ${money(total / 100)} is over the per-claim maximum of ${money(policy.perClaimMax)}.` });
  }

  const byCategory = new Map<Category, number>();
  for (const item of items) {
    byCategory.set(item.category, (byCategory.get(item.category) ?? 0) + cents(item.amount));
    if (cents(item.amount) > cents(policy.receiptRequiredAbove) && !item.hasReceipt) {
      issues.push({
        itemId: item.id,
        message: `${item.description || CATEGORY_LABEL[item.category]} (${money(item.amount)}) needs a receipt; they are required above ${money(policy.receiptRequiredAbove)}.`,
      });
    }
  }
  for (const [category, sum] of byCategory) {
    const limit = policy.categoryLimits[category];
    if (limit != null && sum > cents(limit)) {
      issues.push({ message: `${CATEGORY_LABEL[category]} total ${money(sum / 100)} is over the ${CATEGORY_LABEL[category].toLowerCase()} limit of ${money(limit)}.` });
    }
  }
  return issues;
}

export const needsFinance = (policy: Policy, total: number) => cents(total) > cents(policy.financeApprovalAbove);
