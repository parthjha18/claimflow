import { request } from './client';
import type {
  ClaimDetail, ClaimStatus, ClaimSummary, LineItemInput, Page, Policy, Role, SpendReport, TokenResponse, User,
} from './types';

const v1 = '/api/v1';

export interface PageParams {
  page?: number;
  size?: number;
  sort?: string;
}

export const api = {
  // auth
  login: (email: string, password: string) =>
    request<TokenResponse>(`${v1}/auth/login`, { method: 'POST', body: { email, password }, anonymous: true }),
  register: (b: { organizationName: string; adminFullName: string; adminEmail: string; adminPassword: string }) =>
    request<TokenResponse>(`${v1}/auth/register`, { method: 'POST', body: b, anonymous: true }),

  logout: (refreshToken: string) =>
    request<void>(`${v1}/auth/logout`, { method: 'POST', body: { refreshToken }, anonymous: true }),

  // users
  me: () => request<User>(`${v1}/users/me`),
  listUsers: (p: PageParams = {}) => request<Page<User>>(`${v1}/users`, { query: { size: 100, ...p } }),
  createUser: (b: { email: string; password: string; fullName: string; role: Role; managerId?: string | null }) =>
    request<User>(`${v1}/users`, { method: 'POST', body: b }),
  updateUser: (id: string, b: { fullName?: string; role?: Role; managerId?: string | null; clearManager?: boolean; active?: boolean }) =>
    request<User>(`${v1}/users/${id}`, { method: 'PUT', body: b }),

  // policy
  policy: () => request<Policy>(`${v1}/policy`),
  savePolicy: (b: Omit<Policy, 'currency'>) => request<Policy>(`${v1}/policy`, { method: 'PUT', body: b }),

  // claims
  myClaims: (p: PageParams & { status?: ClaimStatus | '' }) => request<Page<ClaimSummary>>(`${v1}/claims/mine`, { query: p }),
  pendingApprovals: (p: PageParams) => request<Page<ClaimSummary>>(`${v1}/claims/pending-approvals`, { query: p }),
  claim: (id: string) => request<ClaimDetail>(`${v1}/claims/${id}`),
  createClaim: (b: { title: string; description?: string; items?: LineItemInput[] }) =>
    request<ClaimDetail>(`${v1}/claims`, { method: 'POST', body: b }),
  updateClaim: (id: string, b: { title: string; description?: string }) =>
    request<ClaimDetail>(`${v1}/claims/${id}`, { method: 'PUT', body: b }),
  deleteClaim: (id: string) => request<void>(`${v1}/claims/${id}`, { method: 'DELETE' }),
  addItem: (id: string, b: LineItemInput) => request<ClaimDetail>(`${v1}/claims/${id}/items`, { method: 'POST', body: b }),
  updateItem: (id: string, itemId: string, b: LineItemInput) =>
    request<ClaimDetail>(`${v1}/claims/${id}/items/${itemId}`, { method: 'PUT', body: b }),
  removeItem: (id: string, itemId: string) => request<ClaimDetail>(`${v1}/claims/${id}/items/${itemId}`, { method: 'DELETE' }),
  uploadReceipt: (id: string, itemId: string, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return request<ClaimDetail>(`${v1}/claims/${id}/items/${itemId}/receipt`, { method: 'POST', form });
  },
  receipt: (id: string, itemId: string) => request<Blob>(`${v1}/claims/${id}/items/${itemId}/receipt`, { as: 'blob' }),

  // workflow: every call carries an Idempotency-Key so a retried click can never act twice
  submit: (id: string, key: string) => request<ClaimDetail>(`${v1}/claims/${id}/submit`, { method: 'POST', idempotencyKey: key }),
  approve: (id: string, key: string, comment?: string) =>
    request<ClaimDetail>(`${v1}/claims/${id}/approve`, { method: 'POST', idempotencyKey: key, body: { comment } }),
  reject: (id: string, key: string, comment: string) =>
    request<ClaimDetail>(`${v1}/claims/${id}/reject`, { method: 'POST', idempotencyKey: key, body: { comment } }),
  requestChanges: (id: string, key: string, comment: string) =>
    request<ClaimDetail>(`${v1}/claims/${id}/request-changes`, { method: 'POST', idempotencyKey: key, body: { comment } }),
  pay: (id: string, key: string, comment?: string) =>
    request<ClaimDetail>(`${v1}/claims/${id}/pay`, { method: 'POST', idempotencyKey: key, body: { comment } }),

  // reports
  spend: (q: {
    from?: string; to?: string; groupBy?: 'MONTH' | 'CATEGORY' | 'MONTH_CATEGORY'; status?: ClaimStatus[];
    sort?: string; dir?: 'asc' | 'desc'; page?: number; size?: number;
  }) => request<SpendReport>(`${v1}/reports/spend`, { query: q }),
};
