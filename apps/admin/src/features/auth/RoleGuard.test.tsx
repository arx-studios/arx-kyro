import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const replace = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace }),
  usePathname: () => '/queue',
}));
const useMe = vi.fn();
vi.mock('./useMe', () => ({ useMe: () => useMe() }));
vi.mock('./useLogout', () => ({ useLogout: () => ({ mutate: vi.fn(), isPending: false }) }));

import { RoleGuard } from './RoleGuard';

const user = (roles: string[]) => ({ id: '1', email: 'u@kyro.local', displayName: 'U', roles });

describe('RoleGuard', () => {
  beforeEach(() => replace.mockReset());

  it('renders children for VERIFIER', () => {
    useMe.mockReturnValue({ user: user(['VERIFIER']), isLoading: false, error: null });
    render(<RoleGuard><p>secret console</p></RoleGuard>);
    expect(screen.getByText('secret console')).toBeInTheDocument();
  });

  it('renders children for ADMIN', () => {
    useMe.mockReturnValue({ user: user(['ADMIN']), isLoading: false, error: null });
    render(<RoleGuard><p>secret console</p></RoleGuard>);
    expect(screen.getByText('secret console')).toBeInTheDocument();
  });

  it('shows not authorised with a logout button for other roles', () => {
    useMe.mockReturnValue({ user: user(['DONOR']), isLoading: false, error: null });
    render(<RoleGuard><p>secret console</p></RoleGuard>);
    expect(screen.queryByText('secret console')).not.toBeInTheDocument();
    expect(screen.getByText('Not authorised')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument();
  });

  it('redirects to login with next when signed out', () => {
    useMe.mockReturnValue({ user: null, isLoading: false, error: null });
    render(<RoleGuard><p>secret console</p></RoleGuard>);
    expect(screen.queryByText('secret console')).not.toBeInTheDocument();
    expect(replace).toHaveBeenCalledWith('/login?next=%2Fqueue');
  });

  it('shows a loading state while checking', () => {
    useMe.mockReturnValue({ user: null, isLoading: true, error: null });
    render(<RoleGuard><p>secret console</p></RoleGuard>);
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });
});
