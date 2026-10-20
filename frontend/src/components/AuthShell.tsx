import type { ReactNode } from 'react';
import { Icon } from './Icon';

/** Two-column layout shared by the login and register screens. */
export function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="auth">
      <section className="auth-side" aria-hidden="true">
        <div className="brand" style={{ padding: 0 }}>
          <span className="brand-mark" style={{ background: 'rgb(255 255 255 / 20%)' }}>
            <Icon name="check" size={18} stroke="#fff" strokeWidth={2.6} />
          </span>
          ClaimFlow
        </div>
        <div>
          <h1>Expense claims that approve themselves, almost.</h1>
          <p style={{ marginTop: 12 }}>
            File a claim, get it checked against company policy, and follow it from manager to finance to payment.
          </p>
        </div>
        <ul className="auth-points">
          <li><Icon name="policy" /> Policy limits and receipt rules are checked before a claim reaches anyone.</li>
          <li><Icon name="users" /> Maker-checker: nobody can approve their own claim.</li>
          <li><Icon name="bolt" /> Two approvers on the same claim? Exactly one wins, the other is told to reload.</li>
          <li><Icon name="claims" /> Every step is recorded: who, what, when.</li>
        </ul>
      </section>
      <main className="auth-main">
        <div className="auth-card card card-pad">{children}</div>
      </main>
    </div>
  );
}
