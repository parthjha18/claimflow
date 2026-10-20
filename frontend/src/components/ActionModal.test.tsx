import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import { ActionModal } from './ActionModal';

describe('ActionModal', () => {
  it('will not reject or send back without a written reason', async () => {
    const user = userEvent.setup();
    render(<ActionModal action="reject" claimTitle="Trip" onClose={() => {}} onConfirm={vi.fn()} />);
    const confirm = screen.getByRole('button', { name: 'Reject claim' });
    expect(confirm).toBeDisabled();
    await user.type(screen.getByLabelText('Reason for rejecting'), 'Not business related');
    expect(confirm).toBeEnabled();
  });

  it('approving needs no comment', () => {
    render(<ActionModal action="approve" claimTitle="Trip" onClose={() => {}} onConfirm={vi.fn()} />);
    expect(screen.getByRole('button', { name: 'Approve' })).toBeEnabled();
  });

  it('retries with the SAME Idempotency-Key, so a repeated click can never act twice', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn()
      .mockRejectedValueOnce(new ApiError(0, 'NETWORK', 'Could not reach the server'))
      .mockResolvedValueOnce(undefined);
    const onClose = vi.fn();
    render(<ActionModal action="pay" claimTitle="Trip" onClose={onClose} onConfirm={onConfirm} />);

    await user.click(screen.getByRole('button', { name: 'Mark as paid' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the server');
    expect(onClose).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'Mark as paid' }));
    expect(onConfirm).toHaveBeenCalledTimes(2);
    expect(onConfirm.mock.calls[1][2]).toBe(onConfirm.mock.calls[0][2]);   // same key
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('uses a different key for a new decision (a re-opened dialog)', async () => {
    const user = userEvent.setup();
    const keys: string[] = [];
    const onConfirm = vi.fn(async (_a: string, _c: string, key: string) => { keys.push(key); });
    const { rerender } = render(<ActionModal action="approve" claimTitle="Trip" onClose={() => {}} onConfirm={onConfirm} />);
    await user.click(screen.getByRole('button', { name: 'Approve' }));
    rerender(<ActionModal action={null} claimTitle="Trip" onClose={() => {}} onConfirm={onConfirm} />);
    rerender(<ActionModal action="approve" claimTitle="Trip" onClose={() => {}} onConfirm={onConfirm} />);
    await user.click(screen.getByRole('button', { name: 'Approve' }));
    expect(new Set(keys).size).toBe(2);
  });
});
