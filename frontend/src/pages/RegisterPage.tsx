import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ApiError } from '../api/client';
import { AuthShell } from '../components/AuthShell';

export function RegisterPage() {
  const { status, register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ organizationName: '', adminFullName: '', adminEmail: '', adminPassword: '' });
  const [error, setError] = useState<ApiError | string | null>(null);
  const [busy, setBusy] = useState(false);

  if (status === 'authenticated') return <Navigate to="/" replace />;

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [key]: e.target.value }));

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await register({ ...form, organizationName: form.organizationName.trim(), adminEmail: form.adminEmail.trim() });
      navigate('/', { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err : 'Could not reach the server');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell>
      <h2>Create your organisation</h2>
      <p className="muted" style={{ marginBottom: 20 }}>You become the admin and can invite everyone else.</p>
      <form onSubmit={submit}>
        {error && (
          <div className="form-error" role="alert">
            {typeof error === 'string' ? error : error.message}
            {typeof error !== 'string' && error.details.length > 0 && (
              <ul>{error.details.map((d) => <li key={d}>{d}</li>)}</ul>
            )}
          </div>
        )}
        <div className="field">
          <label htmlFor="org">Organisation name</label>
          <input id="org" className="input" required maxLength={200} value={form.organizationName} onChange={set('organizationName')} />
        </div>
        <div className="field">
          <label htmlFor="name">Your name</label>
          <input id="name" className="input" required maxLength={200} value={form.adminFullName} onChange={set('adminFullName')} />
        </div>
        <div className="field">
          <label htmlFor="email">Work email</label>
          <input id="email" className="input" type="email" autoComplete="username" required value={form.adminEmail} onChange={set('adminEmail')} />
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input id="password" className="input" type="password" autoComplete="new-password" required minLength={8} maxLength={72}
            value={form.adminPassword} onChange={set('adminPassword')} />
          <span className="hint">At least 8 characters.</span>
        </div>
        <button className="btn primary block" disabled={busy}>{busy ? 'Creating…' : 'Create organisation'}</button>
      </form>
      <p className="muted" style={{ marginTop: 16, fontSize: '0.9rem' }}>
        Already have an account? <Link to="/login">Sign in</Link>
      </p>
    </AuthShell>
  );
}
