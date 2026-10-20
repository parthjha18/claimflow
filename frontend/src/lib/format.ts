import type { ClaimStatus, ClaimAction, Category, Role } from '../api/types';

const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 });
const inrWhole = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });

export const money = (n: number | null | undefined) => (n == null ? '-' : inr.format(n));
export const moneyWhole = (n: number) => inrWhole.format(n);

export function date(iso: string | null | undefined): string {
  if (!iso) return '-';
  const d = new Date(iso.length === 10 ? `${iso}T00:00:00` : iso);
  return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

export function dateTime(iso: string | null | undefined): string {
  if (!iso) return '-';
  return new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
}

export function monthLabel(yyyyMm: string): string {
  const [y, m] = yyyyMm.split('-').map(Number);
  return new Date(y, m - 1, 1).toLocaleDateString('en-IN', { month: 'short', year: 'numeric' });
}

export const todayIso = () => new Date().toLocaleDateString('en-CA'); // YYYY-MM-DD in local time

export const STATUS_LABEL: Record<ClaimStatus, string> = {
  DRAFT: 'Draft',
  SUBMITTED: 'Awaiting manager',
  MANAGER_APPROVED: 'Manager approved',
  FINANCE_APPROVED: 'Finance approved',
  PAID: 'Paid',
  REJECTED: 'Rejected',
};

export const ACTION_LABEL: Record<ClaimAction, string> = {
  SUBMIT: 'Submitted',
  APPROVE: 'Approved',
  REJECT: 'Rejected',
  REQUEST_CHANGES: 'Requested changes',
  PAY: 'Marked paid',
  ESCALATE: 'Escalated',
};

export const CATEGORY_LABEL: Record<Category, string> = { TRAVEL: 'Travel', FOOD: 'Food', LODGING: 'Lodging', OTHER: 'Other' };
export const ROLE_LABEL: Record<Role, string> = { EMPLOYEE: 'Employee', MANAGER: 'Manager', FINANCE: 'Finance', ADMIN: 'Admin' };
