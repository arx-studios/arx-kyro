import { createRef, useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import {
  Alert,
  Button,
  CaseStatusBadge,
  DateText,
  Dialog,
  EmptyState,
  Field,
  Input,
  MoneyText,
  RequestStatusBadge,
  SandboxBanner,
  Select,
  Textarea,
} from '../index';

describe('Field accessibility', () => {
  it('associates label, hint and error with the control', () => {
    render(
      <Field label="Amount" hint="In rupees" error="Enter an amount." required>
        {(p) => <Input {...p} />}
      </Field>,
    );
    const input = screen.getByLabelText(/Amount/);
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAttribute('aria-required', 'true');
    const described = input.getAttribute('aria-describedby')!.split(' ');
    expect(described).toHaveLength(2);
    expect(document.getElementById(described[0]!)).toHaveTextContent('In rupees');
    expect(screen.getByRole('alert')).toHaveTextContent('Enter an amount.');
    expect(described).toContain(screen.getByRole('alert').id);
  });

  it('has no aria-invalid / describedby when valid and no hint', () => {
    render(<Field label="Name">{(p) => <Input {...p} />}</Field>);
    const input = screen.getByLabelText('Name');
    expect(input).not.toHaveAttribute('aria-invalid');
    expect(input).not.toHaveAttribute('aria-describedby');
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('works with an element child and Textarea/Select', () => {
    render(
      <>
        <Field label="Story" error="Too short">
          <Textarea />
        </Field>
        <Field label="Course">
          <Select>
            <option>A</option>
          </Select>
        </Field>
      </>,
    );
    expect(screen.getByLabelText('Story')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Course')).toBeInTheDocument();
  });

  it('forwards refs', () => {
    const ref = createRef<HTMLInputElement>();
    render(<Input ref={ref} aria-label="x" />);
    expect(ref.current).toBeInstanceOf(HTMLInputElement);
  });
});

describe('Button', () => {
  it('forwards ref, has a visible focus ring class and defaults to type=button', () => {
    const ref = createRef<HTMLButtonElement>();
    render(<Button ref={ref}>Save</Button>);
    expect(ref.current).toBe(screen.getByRole('button', { name: 'Save' }));
    expect(ref.current!.className).toContain('focus-visible:ring-2');
    expect(ref.current).toHaveAttribute('type', 'button');
  });

  it('loading blocks clicks and sets aria-busy', async () => {
    const onClick = vi.fn();
    render(
      <Button loading onClick={onClick}>
        Submit
      </Button>,
    );
    const btn = screen.getByRole('button');
    expect(btn).toBeDisabled();
    expect(btn).toHaveAttribute('aria-busy', 'true');
    await userEvent.click(btn);
    expect(onClick).not.toHaveBeenCalled();
  });
});

describe('Dialog', () => {
  function Harness({ onClose }: { onClose?: () => void }) {
    const [open, setOpen] = useState(false);
    return (
      <>
        <button onClick={() => setOpen(true)}>Open</button>
        <Dialog
          open={open}
          onClose={() => {
            onClose?.();
            setOpen(false);
          }}
          title="Confirm"
          description="Are you sure?"
          footer={<Button>OK</Button>}
        >
          <Input aria-label="Reason" />
        </Dialog>
      </>
    );
  }

  it('is labelled and modal, traps focus, closes on Escape and restores focus', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const opener = screen.getByRole('button', { name: 'Open' });
    await user.click(opener);
    const dialog = screen.getByRole('dialog', { name: 'Confirm' });
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveAccessibleDescription('Are you sure?');
    expect(screen.getByLabelText('Reason')).toHaveFocus();
    await user.tab();
    expect(screen.getByRole('button', { name: 'OK' })).toHaveFocus();
    await user.tab();
    expect(screen.getByLabelText('Reason')).toHaveFocus(); // wrapped
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(opener).toHaveFocus();
  });
});

describe('display components', () => {
  it('status badges show text labels', () => {
    render(
      <>
        <RequestStatusBadge status="CHANGES_REQUESTED" />
        <RequestStatusBadge status="APPROVED" />
        <CaseStatusBadge status="ASSIGNED" />
      </>,
    );
    expect(screen.getByText('Changes requested')).toBeInTheDocument();
    expect(screen.getByText('Verified')).toBeInTheDocument();
    expect(screen.getByText('Assigned')).toBeInTheDocument();
  });

  it('Alert roles depend on tone', () => {
    render(
      <>
        <Alert tone="danger">Bad</Alert>
        <Alert tone="success">Good</Alert>
      </>,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Bad');
    expect(screen.getByRole('status')).toHaveTextContent('Good');
  });

  it('EmptyState, SandboxBanner, MoneyText, DateText render', () => {
    render(
      <>
        <EmptyState title="Nothing here" description="Try later" />
        <SandboxBanner />
        <MoneyText amountMinor={1250050} currency="INR" data-testid="m" />
        <DateText value="2026-11-10T10:00:00Z" timeZone="UTC" data-testid="d" />
      </>,
    );
    expect(screen.getByRole('heading', { name: 'Nothing here' })).toBeInTheDocument();
    expect(screen.getByRole('note', { name: 'Sandbox notice' })).toHaveTextContent(/no real money/i);
    expect(screen.getByTestId('m')).toHaveTextContent('₹12,500.50');
    expect(screen.getByTestId('d')).toHaveAttribute('datetime', '2026-11-10T10:00:00.000Z');
  });
});
