import type { ClaimStatus } from '../api/types';
import { STATUS_LABEL } from '../lib/format';

export function StatusBadge({ status }: { status: ClaimStatus }) {
  return <span className={`badge ${status}`}>{STATUS_LABEL[status]}</span>;
}
