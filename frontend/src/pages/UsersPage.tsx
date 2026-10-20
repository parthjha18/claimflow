import { useMemo, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import { ApiError } from '../api/client';
import type { Role, User } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Icon } from '../components/Icon';
import { Modal } from '../components/Modal';
import { TableSkeleton } from '../components/Skeleton';
import { useToast } from '../components/Toast';
import { ROLE_LABEL } from '../lib/format';

const ROLES: Role[] = ['EMPLOYEE', 'MANAGER', 'FINANCE', 'ADMIN'];

export function UsersPage() {
  const q = useQuery({ queryKey: ['users'], queryFn: () => api.listUsers({ sort: 'fullName,asc' }) });
  const [editing, setEditing] = useState<User | 'new' | null>(null);
  const byId = useMemo(() => new Map((q.data?.content ?? []).map((u) => [u.id, u])), [q.data]);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Users</h1>
          <p>Add people, set who they report to, and choose what they can do.</p>
        </div>
        <button className="btn primary" onClick={() => setEditing('new')}><Icon name="plus" size={16} /> Add user</button>
      </div>

      <div className="card">
        {q.isPending && <TableSkeleton cols={5} />}
        {q.isError && <div className="empty"><h3>Could not load users</h3><button className="btn" onClick={() => q.refetch()}>Try again</button></div>}
        {q.data && (
          <div className="table-wrap">
            <table className="table">
              <thead><tr><th>Name</th><th>Role</th><th>Reports to</th><th>Status</th><th><span className="visually-hidden">Actions</span></th></tr></thead>
              <tbody>
                {q.data.content.map((u) => (
                  <tr key={u.id}>
                    <td><div className="title-cell">{u.fullName}</div><div className="sub">{u.email}</div></td>
                    <td><span className="pill">{ROLE_LABEL[u.role]}</span></td>
                    <td>{u.managerId ? byId.get(u.managerId)?.fullName ?? 'Unknown' : <span className="muted">-</span>}</td>
                    <td>{u.active ? 'Active' : <span className="muted">Deactivated</span>}</td>
                    <td className="right"><button className="btn sm ghost" onClick={() => setEditing(u)} aria-label={`Edit ${u.fullName}`}><Icon name="edit" size={15} /> Edit</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <Modal open={editing !== null} onClose={() => setEditing(null)} title={editing === 'new' ? 'Add user' : 'Edit user'}>
        {editing !== null && (
          <UserForm key={editing === 'new' ? 'new' : editing.id} user={editing === 'new' ? null : editing}
            managers={(q.data?.content ?? []).filter((u) => u.role === 'MANAGER' && u.active)} onClose={() => setEditing(null)} />
        )}
      </Modal>
    </>
  );
}

function UserForm({ user, managers, onClose }: { user: User | null; managers: User[]; onClose: () => void }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const { user: me } = useAuth();
  const [email, setEmail] = useState(user?.email ?? '');
  const [fullName, setFullName] = useState(user?.fullName ?? '');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<Role>(user?.role ?? 'EMPLOYEE');
  const [managerId, setManagerId] = useState(user?.managerId ?? '');
  const [active, setActive] = useState(user?.active ?? true);

  const save = useMutation({
    mutationFn: () => user
      ? api.updateUser(user.id, { fullName, role, active, managerId: managerId || undefined, clearManager: !managerId && !!user.managerId })
      : api.createUser({ email: email.trim(), password, fullName: fullName.trim(), role, managerId: managerId || null }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      toast.success(user ? 'User updated' : 'User added');
      onClose();
    },
  });

  const isSelf = user?.id === me!.id;
  const err = save.error instanceof ApiError ? save.error : null;
  // a manager cannot report to themselves
  const choices = managers.filter((m) => m.id !== user?.id);

  return (
    <form onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate(); }} style={{ marginTop: 14 }}>
      {err && <div className="form-error" role="alert">{err.message}{err.details.length > 0 && <ul>{err.details.map((d) => <li key={d}>{d}</li>)}</ul>}</div>}
      <div className="field">
        <label htmlFor="u-name">Full name</label>
        <input id="u-name" className="input" required maxLength={200} data-autofocus value={fullName} onChange={(e) => setFullName(e.target.value)} />
      </div>
      {!user && (
        <div className="row">
          <div className="field">
            <label htmlFor="u-email">Email</label>
            <input id="u-email" className="input" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="u-pass">Initial password</label>
            <input id="u-pass" className="input" type="password" required minLength={8} maxLength={72} autoComplete="new-password"
              value={password} onChange={(e) => setPassword(e.target.value)} />
          </div>
        </div>
      )}
      <div className="row">
        <div className="field">
          <label htmlFor="u-role">Role</label>
          <select id="u-role" className="input" value={role} onChange={(e) => setRole(e.target.value as Role)} disabled={isSelf}>
            {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
          </select>
          {isSelf && <span className="hint">You cannot change your own role.</span>}
        </div>
        <div className="field">
          <label htmlFor="u-mgr">Reports to</label>
          <select id="u-mgr" className="input" value={managerId} onChange={(e) => setManagerId(e.target.value)}>
            <option value="">No manager</option>
            {choices.map((m) => <option key={m.id} value={m.id}>{m.fullName}</option>)}
          </select>
          <span className="hint">Approves this person's claims.</span>
        </div>
      </div>
      {user && (
        <div className="field">
          <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontWeight: 500 }}>
            <input type="checkbox" checked={active} disabled={isSelf} onChange={(e) => setActive(e.target.checked)} /> Active (can sign in)
          </label>
        </div>
      )}
      <div className="actions" style={{ justifyContent: 'flex-end' }}>
        <button type="button" className="btn" onClick={onClose}>Cancel</button>
        <button className="btn primary" disabled={save.isPending}>{save.isPending ? 'Saving…' : user ? 'Save changes' : 'Add user'}</button>
      </div>
    </form>
  );
}
