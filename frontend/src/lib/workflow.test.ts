import { describe, expect, it } from 'vitest';
import type { ClaimSummary } from '../api/types';
import { availableActions } from './workflow';

const claim = (over: Partial<ClaimSummary>): ClaimSummary => ({
  id: 'c', title: 't', status: 'SUBMITTED', totalAmount: 100, currency: 'INR', claimantId: 'emp',
  claimantName: 'Emp', currentApproverId: 'mgr', currentApproverName: 'Mina Manager', requiresFinance: false,
  submittedAt: null, createdAt: '2026-01-01T00:00:00Z', version: 0, ...over,
});
const user = (id: string, role: 'EMPLOYEE' | 'MANAGER' | 'FINANCE' | 'ADMIN') => ({ id, role });

describe('availableActions', () => {
  it('lets only the current approver decide at the manager stage', () => {
    expect(availableActions(user('mgr', 'MANAGER'), claim({})).actions).toEqual(['approve', 'requestChanges', 'reject']);
    const other = availableActions(user('boss', 'MANAGER'), claim({}));
    expect(other.actions).toEqual([]);
    expect(other.note).toContain('Mina Manager');
  });

  it('never offers actions on your own claim (maker-checker)', () => {
    const res = availableActions(user('emp', 'FINANCE'), claim({ status: 'MANAGER_APPROVED' }));
    expect(res.actions).toEqual([]);
    expect(res.note).toContain('maker-checker');
  });

  it('small claims go from manager approval straight to payment', () => {
    expect(availableActions(user('fin', 'FINANCE'), claim({ status: 'MANAGER_APPROVED', requiresFinance: false })).actions[0]).toBe('pay');
  });

  it('large claims need a finance approval before payment', () => {
    expect(availableActions(user('fin', 'FINANCE'), claim({ status: 'MANAGER_APPROVED', requiresFinance: true })).actions[0]).toBe('approve');
    expect(availableActions(user('fin', 'FINANCE'), claim({ status: 'FINANCE_APPROVED', requiresFinance: true })).actions[0]).toBe('pay');
  });

  it('shows nothing for drafts and finished claims, and nothing to non-finance after the manager stage', () => {
    for (const status of ['DRAFT', 'PAID', 'REJECTED'] as const) {
      expect(availableActions(user('fin', 'FINANCE'), claim({ status })).actions).toEqual([]);
    }
    expect(availableActions(user('mgr', 'MANAGER'), claim({ status: 'MANAGER_APPROVED' })).actions).toEqual([]);
  });
});
