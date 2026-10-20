import type { ApprovalStep } from '../api/types';
import { ACTION_LABEL, dateTime, STATUS_LABEL } from '../lib/format';

export function HistoryTimeline({ steps }: { steps: ApprovalStep[] }) {
  if (steps.length === 0) return <p className="muted">Nothing has happened to this claim yet.</p>;
  return (
    <ol className="timeline">
      {steps.map((s) => (
        <li key={s.id} className={s.action}>
          <div className="what">
            {s.action === 'ESCALATE'
              ? <strong>Escalated automatically</strong>
              : <><strong>{s.actorName}</strong> {ACTION_LABEL[s.action].toLowerCase()}</>}
            {s.action !== 'ESCALATE' && s.fromStatus !== s.toStatus && (
              <span className="muted"> · {STATUS_LABEL[s.fromStatus]} → {STATUS_LABEL[s.toStatus]}</span>
            )}
          </div>
          {s.comment && <blockquote>{s.comment}</blockquote>}
          <time className="muted" dateTime={s.at}>{dateTime(s.at)}</time>
        </li>
      ))}
    </ol>
  );
}
