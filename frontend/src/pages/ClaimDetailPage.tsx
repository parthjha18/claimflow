import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import { ApiError, newIdempotencyKey } from '../api/client';
import type { ClaimDetail, LineItem, LineItemInput } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { ActionModal, ACTION_COPY } from '../components/ActionModal';
import { ClaimInfoModal } from '../components/ClaimInfoModal';
import { ConfirmModal } from '../components/ConfirmModal';
import { HistoryTimeline } from '../components/HistoryTimeline';
import { Icon } from '../components/Icon';
import { ItemModal } from '../components/ItemModal';
import { PolicyPanel } from '../components/PolicyPanel';
import { Stepper } from '../components/Stepper';
import { StatusBadge } from '../components/StatusBadge';
import { TableSkeleton } from '../components/Skeleton';
import { useToast } from '../components/Toast';
import { CATEGORY_LABEL, date, dateTime, money } from '../lib/format';
import { checkPolicy, needsFinance } from '../lib/policyCheck';
import { availableActions, type WorkflowAction } from '../lib/workflow';

export function ClaimDetailPage() {
  const { id = '' } = useParams();
  const { user } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();

  const q = useQuery({ queryKey: ['claim', id], queryFn: () => api.claim(id) });
  const policyQ = useQuery({ queryKey: ['policy'], queryFn: api.policy, staleTime: 60_000 });

  const [itemModal, setItemModal] = useState<{ item: LineItem | null } | null>(null);
  const [infoOpen, setInfoOpen] = useState(false);
  const [action, setAction] = useState<WorkflowAction | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [busy, setBusy] = useState(false);
  const [submitErrors, setSubmitErrors] = useState<string[] | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const uploadTarget = useRef<string | null>(null);
  const submitKey = useRef<string | null>(null);

  // arriving from "New claim": open the add-item dialog straight away
  useEffect(() => {
    if ((location.state as { addItem?: boolean } | null)?.addItem && q.data) {
      setItemModal({ item: null });
      navigate(location.pathname, { replace: true, state: null });
    }
  }, [q.data, location, navigate]);

  const detail = q.data;
  const claim = detail?.claim;
  const issues = useMemo(
    () => (detail && policyQ.data ? checkPolicy(policyQ.data, detail.items) : []),
    [detail, policyQ.data],
  );

  if (q.isPending) return <div className="card"><TableSkeleton rows={6} /></div>;
  if (q.isError || !detail || !claim) {
    const notFound = q.error instanceof ApiError && q.error.status === 404;
    return (
      <div className="card empty">
        <h3>{notFound ? 'Claim not found' : 'Could not load this claim'}</h3>
        <p>{notFound ? 'It may not exist, or you may not have access to it.' : 'Please try again.'}</p>
        <p style={{ marginTop: 14 }}><Link to="/claims" className="btn">Back to my claims</Link></p>
      </div>
    );
  }

  const me = user!;
  const isOwner = claim.claimantId === me.id;
  const editable = isOwner && claim.status === 'DRAFT';
  const { actions, note } = availableActions(me, claim);
  const financeNeeded = claim.status === 'DRAFT' && policyQ.data ? needsFinance(policyQ.data, claim.totalAmount) : claim.requiresFinance;

  // ---- cache + messages ----------------------------------------------------------------------------------
  const applyDetail = (d: ClaimDetail) => {
    queryClient.setQueryData(['claim', id], d);
    queryClient.invalidateQueries({ queryKey: ['claims'] });
    queryClient.invalidateQueries({ queryKey: ['approvals'] });
    queryClient.invalidateQueries({ queryKey: ['pending-count'] });
    queryClient.invalidateQueries({ queryKey: ['reports'] });
  };

  async function guarded<T>(fn: () => Promise<T>): Promise<T | undefined> {
    setBusy(true);
    try {
      return await fn();
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        // someone else got there first: show the latest state instead of letting the user act on stale data
        toast.error(err);
        queryClient.invalidateQueries({ queryKey: ['claim', id] });
      } else {
        toast.error(err);
      }
      return undefined;
    } finally {
      setBusy(false);
    }
  }

  // ---- draft editing -------------------------------------------------------------------------------------
  const saveItem = async (input: LineItemInput) => {
    const item = itemModal?.item;
    applyDetail(item ? await api.updateItem(id, item.id, input) : await api.addItem(id, input));
    toast.success(item ? 'Item updated' : 'Item added');
  };

  const removeItem = (item: LineItem) =>
    guarded(async () => { applyDetail(await api.removeItem(id, item.id)); toast.success('Item removed'); });

  const pickReceipt = (item: LineItem) => {
    uploadTarget.current = item.id;
    fileInput.current?.click();
  };

  const onFileChosen = async (file: File | undefined) => {
    const itemId = uploadTarget.current;
    if (fileInput.current) fileInput.current.value = '';
    if (!file || !itemId) return;
    await guarded(async () => { applyDetail(await api.uploadReceipt(id, itemId, file)); toast.success('Receipt attached'); });
  };

  const viewReceipt = async (item: LineItem) => {
    const win = window.open('', '_blank'); // opened synchronously so the popup blocker allows it
    try {
      const blob = await api.receipt(id, item.id);
      const url = URL.createObjectURL(blob);
      if (win) win.location.href = url; else window.location.assign(url);
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      win?.close();
      toast.error(err);
    }
  };

  const submit = () => guarded(async () => {
    setSubmitErrors(null);
    submitKey.current ??= newIdempotencyKey();
    try {
      applyDetail(await api.submit(id, submitKey.current));
      submitKey.current = null;
      toast.success('Claim submitted for approval');
    } catch (err) {
      if (err instanceof ApiError && err.status === 422) {
        submitKey.current = null; // a rejected attempt does not consume the key, but a fixed claim is a new attempt
        setSubmitErrors(err.details.length ? err.details : [err.message]);
        return;
      }
      throw err;
    }
  });

  const deleteDraft = () => guarded(async () => {
    await api.deleteClaim(id);
    queryClient.removeQueries({ queryKey: ['claim', id] });
    queryClient.invalidateQueries({ queryKey: ['claims'] });
    toast.success('Draft deleted');
    navigate('/claims', { replace: true });
  });

  // ---- approvals -----------------------------------------------------------------------------------------
  const runAction = async (a: WorkflowAction, comment: string, key: string) => {
    const result = await (a === 'approve' ? api.approve(id, key, comment || undefined)
      : a === 'pay' ? api.pay(id, key, comment || undefined)
      : a === 'reject' ? api.reject(id, key, comment)
      : api.requestChanges(id, key, comment));
    applyDetail(result);
    toast.success(({ approve: 'Claim approved', pay: 'Marked as paid', reject: 'Claim rejected', requestChanges: 'Sent back to the employee' })[a]);
  };

  const onActionConfirm = async (a: WorkflowAction, comment: string, key: string) => {
    try {
      await runAction(a, comment, key);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        // lost a race with another approver (or the claim moved on): refresh and explain
        toast.error(err);
        queryClient.invalidateQueries({ queryKey: ['claim', id] });
        setAction(null);
        return;
      }
      throw err; // the dialog shows other errors inline and keeps the same key for a safe retry
    }
  };

  return (
    <>
      <Link to={isOwner ? '/claims' : '/approvals'} className="back-link"><Icon name="arrowLeft" size={16} /> {isOwner ? 'My claims' : 'Approvals'}</Link>

      <div className="page-head">
        <div>
          <h1>{claim.title}</h1>
          <p>
            Filed by {isOwner ? 'you' : claim.claimantName}
            {claim.submittedAt && <> · submitted {dateTime(claim.submittedAt)}</>}
            {' '}· <StatusBadge status={claim.status} />
          </p>
        </div>
        <div style={{ textAlign: 'right' }}>
          <div className="big-amount num">{money(claim.totalAmount)}</div>
          {claim.requiresFinance && <span className="pill">Finance approval needed</span>}
        </div>
      </div>

      <div className="stack">
        <div className="card card-pad">
          <Stepper status={claim.status} requiresFinance={financeNeeded} history={detail.history} />
        </div>

        {note && <div className="banner">{note}</div>}
        {claim.status === 'REJECTED' && <div className="banner danger">This claim was rejected. See the history for the reason.</div>}
        {submitErrors && (
          <div className="banner warn" role="alert">
            <strong>This claim cannot be submitted yet.</strong>
            <ul>{submitErrors.map((e) => <li key={e}>{e}</li>)}</ul>
          </div>
        )}

        {actions.length > 0 && (
          <div className="card card-pad" style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
            <strong style={{ marginRight: 'auto' }}>Your decision</strong>
            {actions.map((a) => (
              <button key={a} className={`btn ${a === 'approve' || a === 'pay' ? 'primary' : a === 'reject' ? 'danger' : ''}`}
                onClick={() => setAction(a)} disabled={busy}>
                {ACTION_COPY[a].confirm}
              </button>
            ))}
          </div>
        )}

        <div className="detail-grid">
          <div className="stack">
            <div className="card">
              <div className="card-head">
                <h2>Expenses</h2>
                {editable && (
                  <button className="btn sm" onClick={() => setItemModal({ item: null })}><Icon name="plus" size={15} /> Add item</button>
                )}
              </div>
              {detail.items.length === 0 ? (
                <div className="empty"><h3>No expenses yet</h3><p>{editable ? 'Add the first line item to this claim.' : 'This claim has no items.'}</p></div>
              ) : (
                <div className="table-wrap">
                  <table className="table items-table">
                    <thead>
                      <tr><th>Date</th><th>Expense</th><th className="right">Amount</th><th>Receipt</th>{editable && <th><span className="visually-hidden">Actions</span></th>}</tr>
                    </thead>
                    <tbody>
                      {detail.items.map((item) => {
                        const flagged = issues.some((i) => i.itemId === item.id);
                        return (
                          <tr key={item.id} className={editable && flagged ? 'row-flag' : undefined}>
                            <td className="nowrap">{date(item.expenseDate)}</td>
                            <td>
                              <div className="title-cell">{CATEGORY_LABEL[item.category]}</div>
                              {item.description && <div className="sub">{item.description}</div>}
                            </td>
                            <td className="right num">{money(item.amount)}</td>
                            <td>
                              {item.hasReceipt ? (
                                <span className="actions" style={{ flexWrap: 'nowrap' }}>
                                  <button className="btn sm ghost" onClick={() => viewReceipt(item)}><Icon name="clip" size={15} /> {item.receiptFilename ?? 'View'}</button>
                                  {editable && <button className="btn sm ghost" onClick={() => pickReceipt(item)} disabled={busy}>Replace</button>}
                                </span>
                              ) : editable ? (
                                <button className="btn sm" onClick={() => pickReceipt(item)} disabled={busy}>
                                  <Icon name="clip" size={15} /> Attach{flagged && <span className="receipt-flag"> · required</span>}
                                </button>
                              ) : <span className="muted">None</span>}
                            </td>
                            {editable && (
                              <td className="nowrap right">
                                <button className="btn sm ghost" aria-label={`Edit ${CATEGORY_LABEL[item.category]} item of ${money(item.amount)} on ${date(item.expenseDate)}`} onClick={() => setItemModal({ item })}><Icon name="edit" size={15} /></button>
                                <button className="btn sm ghost" aria-label={`Remove ${CATEGORY_LABEL[item.category]} item of ${money(item.amount)} on ${date(item.expenseDate)}`} onClick={() => removeItem(item)} disabled={busy}><Icon name="trash" size={15} /></button>
                              </td>
                            )}
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
              <input ref={fileInput} type="file" accept="application/pdf,image/png,image/jpeg" className="visually-hidden"
                aria-label="Receipt file" onChange={(e) => onFileChosen(e.target.files?.[0])} />
            </div>

            <div className="card card-pad">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                <h2>Details</h2>
                {editable && <button className="btn sm ghost" onClick={() => setInfoOpen(true)}><Icon name="edit" size={15} /> Edit</button>}
              </div>
              <dl className="kv">
                <dt>Description</dt><dd>{detail.description || <span className="muted">No description</span>}</dd>
                <dt>Filed by</dt><dd>{claim.claimantName}</dd>
                {claim.currentApproverName && claim.status === 'SUBMITTED' && <><dt>With</dt><dd>{claim.currentApproverName}</dd></>}
                <dt>Created</dt><dd>{dateTime(claim.createdAt)}</dd>
              </dl>
            </div>
          </div>

          <div className="stack">
            {editable && policyQ.data && (
              <>
                <PolicyPanel issues={issues} policy={policyQ.data} total={claim.totalAmount} financeNeeded={financeNeeded}
                  approverName={null} />
                <div className="card card-pad" style={{ display: 'grid', gap: 10 }}>
                  <button className="btn primary block" onClick={submit} disabled={busy || detail.items.length === 0}>
                    {busy ? 'Working…' : 'Submit for approval'}
                  </button>
                  <button className="btn danger block" onClick={() => setConfirmDelete(true)} disabled={busy}>Delete draft</button>
                </div>
              </>
            )}
            <div className="card card-pad">
              <h2 style={{ marginBottom: 14 }}>History</h2>
              <HistoryTimeline steps={detail.history} />
            </div>
          </div>
        </div>
      </div>

      <ItemModal open={itemModal !== null} onClose={() => setItemModal(null)} item={itemModal?.item ?? null}
        policy={policyQ.data} onSave={saveItem} />
      <ClaimInfoModal open={infoOpen} onClose={() => setInfoOpen(false)} title={claim.title} description={detail.description}
        onSave={async (title, description) => { applyDetail(await api.updateClaim(id, { title, description })); toast.success('Details saved'); }} />
      <ActionModal action={action} claimTitle={claim.title} onClose={() => setAction(null)} onConfirm={onActionConfirm} />
      <ConfirmModal open={confirmDelete} title="Delete this draft?" message="The draft and its receipts will be permanently removed."
        confirmLabel="Delete draft" busy={busy} onClose={() => setConfirmDelete(false)}
        onConfirm={async () => { await deleteDraft(); setConfirmDelete(false); }} />
    </>
  );
}
