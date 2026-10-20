import type { ApprovalStep, ClaimStatus } from '../api/types';
import { STATUS_LABEL } from '../lib/format';
import { Icon } from './Icon';

/** The happy-path stages of a claim; finance approval only exists for claims over the finance threshold. */
export function Stepper({ status, requiresFinance, history }: {
  status: ClaimStatus; requiresFinance: boolean; history: ApprovalStep[];
}) {
  const stages: ClaimStatus[] = requiresFinance
    ? ['DRAFT', 'SUBMITTED', 'MANAGER_APPROVED', 'FINANCE_APPROVED', 'PAID']
    : ['DRAFT', 'SUBMITTED', 'MANAGER_APPROVED', 'PAID'];

  // How far did it get? For REJECTED, the furthest stage it reached before that.
  const reached = status === 'REJECTED'
    ? Math.max(0, ...history.map((h) => stages.indexOf(h.toStatus)).concat(history.length ? [1] : [0]))
    : Math.max(0, stages.indexOf(status));

  return (
    <ol className="stepper" aria-label="Claim progress">
      {stages.map((s, i) => {
        const state = status === 'REJECTED' ? (i <= reached ? 'done' : 'todo')
          : i < reached || status === 'PAID' ? 'done' : i === reached ? 'current' : 'todo';
        return (
          <li key={s} className={`step ${state}`} aria-current={state === 'current' ? 'step' : undefined}>
            <span className="dot">{state === 'done' ? <Icon name="check" size={13} strokeWidth={3} /> : i + 1}</span>
            <span className="label">{s === 'SUBMITTED' ? 'Manager review' : STATUS_LABEL[s]}</span>
          </li>
        );
      })}
      {status === 'REJECTED' && (
        <li className="step rejected"><span className="dot">×</span><span className="label">Rejected</span></li>
      )}
    </ol>
  );
}
