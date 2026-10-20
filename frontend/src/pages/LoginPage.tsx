import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ApiError } from '../api/client';
import { AuthShell } from '../components/AuthShell';

const DEMO = [
  { label: 'Employee', email: 'anita.employee@acme.demo' },
  { label: 'Manager', email: 'rahul.manager@acme.demo' },
  { label: 'Finance', email: 'meera.finance@acme.demo' },
  { label: 'Admin', email: 'admin@acme.demo' },
];
const DEMO_PASSWORD = 'Demo@1234';

export function LoginPage() {
  const { status, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (status === 'authenticated') return <Navigate to={from} replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await login(email.trim(), password);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not reach the server');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell>
      <h2>Sign in</h2>
      <p className="muted" style={{ marginBottom: 20 }}>Welcome back. Enter your work account.</p>
      <form onSubmit={submit}>
        {error && <div className="form-error" role="alert">{error}</div>}
        <div className="field">
          <label htmlFor="email">Email</label>
          <input id="email" className="input" type="email" autoComplete="username" required
            value={email} onChange={(e) => setEmail(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input id="password" className="input" type="password" autoComplete="current-password" required
            value={password} onChange={(e) => setPassword(e.target.value)} />
        </div>
        <button className="btn primary block" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
      </form>
      <p className="muted" style={{ marginTop: 16, fontSize: '0.9rem' }}>
        New company? <Link to="/register">Create an organisation</Link>
      </p>
      <div className="demo-box">
        <div className="label">Demo accounts</div>
        <div className="chips">
          {DEMO.map((d) => (
            <button key={d.email} type="button" className="chip"
              onClick={() => { setEmail(d.email); setPassword(DEMO_PASSWORD); setError(null); }}>
              {d.label}
            </button>
          ))}
        </div>
        <p className="muted" style={{ fontSize: '0.8rem', marginTop: 8 }}>
          Fills the form for the sample company that <code>docker compose up</code> loads.
        </p>
      </div>
    </AuthShell>
  );
}
