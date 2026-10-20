import type { ClaimSummary, Role } from '../api/types';

export type WorkflowAction = 'approve' | 'pay' | 'reject' | 'requestChanges';

export interface ActionAvailability {
  actions: WorkflowAction[];
  /** Why the viewer cannot act, when that is worth telling them. */
  note?: string;
}

/**
 * What the signed-in user may do to a claim right now. This only decides which buttons to show; the server
 * enforces the same rules (maker-checker, stage authority) and is the real gatekeeper.
 */
export function availableActions(user: { id: string; role: Role }, c: ClaimSummary): ActionAvailability {
  const pending = c.status === 'SUBMITTED' || c.status === 'MANAGER_APPROVED' || c.status === 'FINANCE_APPROVED';
  if (!pending) return { actions: [] };

  if (user.id === c.claimantId) {
    const waiting = c.status === 'SUBMITTED' ? `Waiting for ${c.currentApproverName ?? 'your manager'} to review.`
      : c.status === 'MANAGER_APPROVED' ? (c.requiresFinance ? 'Your manager approved it. Waiting for the finance team.' : 'Your manager approved it. Waiting for payment.')
      : 'Finance approved it. Waiting for payment.';
    // approvers and finance staff file claims too: say plainly why they get no buttons on their own
    const why = user.role === 'MANAGER' || user.role === 'FINANCE' ? ' You cannot decide on your own claim (maker-checker).' : '';
    return { actions: [], note: waiting + why };
  }

  switch (c.status) {
    case 'SUBMITTED':
      return user.role === 'MANAGER' && user.id === c.currentApproverId
        ? { actions: ['approve', 'requestChanges', 'reject'] }
        : { actions: [], note: `Waiting for ${c.currentApproverName ?? 'the manager'} to decide.` };
    case 'MANAGER_APPROVED':
      if (user.role !== 'FINANCE') return { actions: [], note: 'Waiting for the finance team.' };
      return { actions: [c.requiresFinance ? 'approve' : 'pay', 'requestChanges', 'reject'] };
    case 'FINANCE_APPROVED':
      return user.role === 'FINANCE'
        ? { actions: ['pay', 'requestChanges', 'reject'] }
        : { actions: [], note: 'Approved. Waiting for the finance team to pay it.' };
    default:
      return { actions: [] };
  }
}
