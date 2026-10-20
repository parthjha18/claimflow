package com.claimflow.claim.domain;

/**
 * The only place that knows which status changes are legal. Pure and stateless, so it is unit-testable
 * without Spring or a database. Services call {@link #next} before touching an entity and let the
 * exception abort the request if the move is illegal.
 *
 * <pre>
 * DRAFT --SUBMIT--> SUBMITTED --APPROVE--> MANAGER_APPROVED --APPROVE--> FINANCE_APPROVED --PAY--> PAID
 *                                              |  (requiresFinance only)
 *                                              +--PAY (requiresFinance == false)--------------------> PAID
 * SUBMITTED / MANAGER_APPROVED / FINANCE_APPROVED --REJECT--> REJECTED
 * SUBMITTED / MANAGER_APPROVED / FINANCE_APPROVED --REQUEST_CHANGES--> DRAFT
 * SUBMITTED --ESCALATE--> SUBMITTED (approver changes, status does not)
 * </pre>
 */
public final class ClaimStateMachine {

    private ClaimStateMachine() {
    }

    public static ClaimStatus next(ClaimStatus current, ClaimAction action, boolean requiresFinance) {
        ClaimStatus next = resolve(current, action, requiresFinance);
        if (next == null) {
            throw new InvalidStateTransitionException(current, action);
        }
        return next;
    }

    public static boolean canTransition(ClaimStatus current, ClaimAction action, boolean requiresFinance) {
        return resolve(current, action, requiresFinance) != null;
    }

    private static ClaimStatus resolve(ClaimStatus current, ClaimAction action, boolean requiresFinance) {
        return switch (current) {
            case DRAFT -> action == ClaimAction.SUBMIT ? ClaimStatus.SUBMITTED : null;
            case SUBMITTED -> switch (action) {
                case APPROVE -> ClaimStatus.MANAGER_APPROVED;
                case REJECT -> ClaimStatus.REJECTED;
                case REQUEST_CHANGES -> ClaimStatus.DRAFT;
                case ESCALATE -> ClaimStatus.SUBMITTED;
                default -> null;
            };
            case MANAGER_APPROVED -> switch (action) {
                case APPROVE -> requiresFinance ? ClaimStatus.FINANCE_APPROVED : null;
                case PAY -> requiresFinance ? null : ClaimStatus.PAID;
                case REJECT -> ClaimStatus.REJECTED;
                case REQUEST_CHANGES -> ClaimStatus.DRAFT;
                default -> null;
            };
            case FINANCE_APPROVED -> switch (action) {
                case PAY -> ClaimStatus.PAID;
                case REJECT -> ClaimStatus.REJECTED;
                case REQUEST_CHANGES -> ClaimStatus.DRAFT;
                default -> null;
            };
            case PAID, REJECTED -> null;
        };
    }
}
