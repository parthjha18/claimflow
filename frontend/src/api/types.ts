// Shapes returned by the ClaimFlow API (see the Java DTO records).

export type Role = 'EMPLOYEE' | 'MANAGER' | 'FINANCE' | 'ADMIN';
export type Category = 'TRAVEL' | 'FOOD' | 'LODGING' | 'OTHER';
export type ClaimStatus = 'DRAFT' | 'SUBMITTED' | 'MANAGER_APPROVED' | 'FINANCE_APPROVED' | 'PAID' | 'REJECTED';
export type ClaimAction = 'SUBMIT' | 'APPROVE' | 'REJECT' | 'REQUEST_CHANGES' | 'PAY' | 'ESCALATE';

export const CATEGORIES: Category[] = ['TRAVEL', 'FOOD', 'LODGING', 'OTHER'];

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  userId: string;
  organizationId: string;
  role: Role;
}

export interface User {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  managerId: string | null;
  active: boolean;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ClaimSummary {
  id: string;
  title: string;
  status: ClaimStatus;
  totalAmount: number;
  currency: string;
  claimantId: string;
  claimantName: string;
  currentApproverId: string | null;
  currentApproverName: string | null;
  requiresFinance: boolean;
  submittedAt: string | null;
  createdAt: string;
  version: number;
}

export interface LineItem {
  id: string;
  category: Category;
  amount: number;
  expenseDate: string;
  description: string | null;
  hasReceipt: boolean;
  receiptFilename: string | null;
}

export interface ApprovalStep {
  id: string;
  actorId: string;
  actorName: string;
  action: ClaimAction;
  fromStatus: ClaimStatus;
  toStatus: ClaimStatus;
  comment: string | null;
  at: string;
}

export interface ClaimDetail {
  claim: ClaimSummary;
  description: string | null;
  items: LineItem[];
  history: ApprovalStep[];
}

export interface Policy {
  perClaimMax: number;
  receiptRequiredAbove: number;
  financeApprovalAbove: number;
  escalationAfterDays: number;
  categoryLimits: Partial<Record<Category, number>>;
  currency: string;
}

export interface SpendRow {
  month: string | null;
  category: Category | null;
  total: number;
  itemCount: number;
  claimCount: number;
}

export interface SpendReport {
  grandTotal: number;
  currency: string;
  rows: Page<SpendRow>;
}

export interface LineItemInput {
  category: Category;
  amount: number;
  expenseDate: string;
  description?: string;
}
