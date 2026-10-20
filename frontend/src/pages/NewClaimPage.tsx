import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import { ApiError } from '../api/client';
import { Icon } from '../components/Icon';

export function NewClaimPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');

  const create = useMutation({
    mutationFn: () => api.createClaim({ title: title.trim(), description: description.trim() || undefined }),
    onSuccess: (detail) => {
      queryClient.invalidateQueries({ queryKey: ['claims'] });
      queryClient.setQueryData(['claim', detail.claim.id], detail);
      navigate(`/claims/${detail.claim.id}`, { replace: true, state: { addItem: true } });
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    create.mutate();
  }

  const err = create.error instanceof ApiError ? create.error : null;

  return (
    <>
      <Link to="/claims" className="muted" style={{ display: 'inline-flex', gap: 6, alignItems: 'center', marginBottom: 14 }}>
        <Icon name="arrowLeft" size={16} /> My claims
      </Link>
      <div className="page-head">
        <div>
          <h1>New claim</h1>
          <p>Start with a title. You will add the individual expenses and receipts next.</p>
        </div>
      </div>
      <form className="card card-pad" style={{ maxWidth: 640 }} onSubmit={submit}>
        {err && (
          <div className="form-error" role="alert">
            {err.message}
            {err.details.length > 0 && <ul>{err.details.map((d) => <li key={d}>{d}</li>)}</ul>}
          </div>
        )}
        <div className="field">
          <label htmlFor="title">Title</label>
          <input id="title" className="input" required maxLength={200} autoFocus placeholder="e.g. Client visit, Pune"
            value={title} onChange={(e) => setTitle(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="description">Description <span className="muted">(optional)</span></label>
          <textarea id="description" className="input" maxLength={2000} placeholder="What was the trip or purchase for?"
            value={description} onChange={(e) => setDescription(e.target.value)} />
        </div>
        <div className="actions">
          <button className="btn primary" disabled={create.isPending || !title.trim()}>
            {create.isPending ? 'Creating…' : 'Create draft'}
          </button>
          <Link to="/claims" className="btn">Cancel</Link>
        </div>
      </form>
    </>
  );
}
