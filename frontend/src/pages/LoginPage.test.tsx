import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import { api } from '../api/endpoints';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../components/Toast';
import { LoginPage } from './LoginPage';

vi.mock('../api/endpoints', () => ({ api: { login: vi.fn(), me: vi.fn() } }));

function renderLogin() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={['/login']}>
        <ToastProvider><AuthProvider><LoginPage /></AuthProvider></ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('LoginPage', () => {
  beforeEach(() => { localStorage.clear(); vi.mocked(api.login).mockReset(); });

  it('shows the server message when the password is wrong', async () => {
    vi.mocked(api.login).mockRejectedValue(new ApiError(401, 'INVALID_CREDENTIALS', 'Invalid email or password'));
    const user = userEvent.setup();
    renderLogin();
    await user.type(screen.getByLabelText('Email'), 'a@b.com');
    await user.type(screen.getByLabelText('Password'), 'wrong');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password');
  });

  it('demo chips fill in the credentials', async () => {
    const user = userEvent.setup();
    renderLogin();
    await user.click(screen.getByRole('button', { name: 'Finance' }));
    expect(screen.getByLabelText('Email')).toHaveValue('meera.finance@acme.demo');
    expect(screen.getByLabelText('Password')).toHaveValue('Demo@1234');
  });
});
