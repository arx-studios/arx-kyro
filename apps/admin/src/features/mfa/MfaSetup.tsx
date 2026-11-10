'use client';

import { useEffect, useState, type FormEvent } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useQueryClient } from '@tanstack/react-query';
import QRCode from 'qrcode';
import { ApiError, unwrap, type TotpEnrollment } from '@kyro/api-client';
import { Alert, Button, Field, Input } from '@kyro/design-system';
import { api } from '@/lib/api';
import { safeNext } from '@/lib/decision';
import { TOTP_PATTERN } from '@/lib/mfa';
import { describeError, type FriendlyProblem } from '@/lib/problems';
import { queryKeys } from '@/lib/queryKeys';

type Step = 'intro' | 'scan' | 'codes';

/**
 * Secrets, the QR image and recovery codes live only in this component's state. They are never put in
 * the query cache (plain calls, not useQuery/useMutation), storage or the URL.
 */
export function MfaSetup() {
  const router = useRouter();
  const params = useSearchParams();
  const queryClient = useQueryClient();
  const [step, setStep] = useState<Step>('intro');
  const [enrollment, setEnrollment] = useState<TotpEnrollment | null>(null);
  const [qr, setQr] = useState<string | null>(null);
  const [codes, setCodes] = useState<string[]>([]);
  const [code, setCode] = useState('');
  const [fieldError, setFieldError] = useState<string | undefined>();
  const [problem, setProblem] = useState<FriendlyProblem | null>(null);
  const [pending, setPending] = useState(false);
  const [acknowledged, setAcknowledged] = useState(false);

  useEffect(() => {
    if (!enrollment) return;
    let cancelled = false;
    QRCode.toDataURL(enrollment.otpauthUri, { margin: 1, width: 224, errorCorrectionLevel: 'M' })
      .then((url) => {
        if (!cancelled) setQr(url);
      })
      .catch(() => {
        if (!cancelled) setQr(null);
      });
    return () => {
      cancelled = true;
    };
  }, [enrollment]);

  async function begin() {
    setPending(true);
    setProblem(null);
    try {
      setEnrollment(unwrap(await api.POST('/api/v1/auth/mfa/totp/enrollment')));
      setStep('scan');
    } catch (err) {
      if (err instanceof ApiError && err.code === 'MFA_ALREADY_ENROLLED') {
        await queryClient.invalidateQueries({ queryKey: queryKeys.me });
      }
      setProblem(describeError(err));
    } finally {
      setPending(false);
    }
  }

  async function confirm(e: FormEvent) {
    e.preventDefault();
    const value = code.trim();
    if (!TOTP_PATTERN.test(value)) {
      setFieldError('Enter the 6-digit code from your authenticator app.');
      return;
    }
    setFieldError(undefined);
    setProblem(null);
    setPending(true);
    try {
      const result = unwrap(await api.POST('/api/v1/auth/mfa/totp/enrollment/confirm', { body: { code: value } }));
      setCodes(result.codes);
      setEnrollment(null);
      setQr(null);
      setCode('');
      setStep('codes');
    } catch (err) {
      setProblem(describeError(err));
      setCode('');
    } finally {
      setPending(false);
    }
  }

  async function finish() {
    if (!acknowledged) return;
    setCodes([]);
    await queryClient.invalidateQueries({ queryKey: queryKeys.me });
    router.replace(safeNext(params?.get('next') ?? '/'));
  }

  const alert = problem ? (
    <Alert tone={problem.tone} title={problem.title}>
      <p>{problem.message}</p>
    </Alert>
  ) : null;

  if (step === 'intro') {
    return (
      <div className="space-y-4">
        <p className="text-sm text-charcoal-700">
          Staff accounts need a second step when signing in. You will link an authenticator app (any TOTP app),
          then save one-time recovery codes in case you lose the device.
        </p>
        {alert}
        <Button onClick={begin} loading={pending}>
          Begin setup
        </Button>
      </div>
    );
  }

  if (step === 'scan' && enrollment) {
    return (
      <form onSubmit={confirm} noValidate className="space-y-4" aria-label="Link authenticator app">
        <ol className="list-decimal space-y-1 pl-5 text-sm text-charcoal-700">
          <li>Scan the QR code with your authenticator app, or enter the secret manually.</li>
          <li>Enter the 6-digit code the app shows.</li>
        </ol>
        {qr ? (
          <img
            src={qr}
            alt={`QR code for ${enrollment.accountName}. Use the setup key below if you cannot scan it.`}
            width={224}
            height={224}
            className="mx-auto rounded-md border border-warm-200"
          />
        ) : null}
        <div>
          <p className="mb-1 text-sm font-medium text-charcoal-900">Setup key (manual entry)</p>
          <div className="flex flex-wrap items-center gap-2">
            <code
              data-testid="totp-secret"
              className="break-all rounded-md bg-warm-100 px-3 py-2 font-mono text-sm tracking-wider"
            >
              {enrollment.secret}
            </code>
            <CopyButton text={enrollment.secret} label="Copy key" />
          </div>
          <p className="mt-1 text-xs text-charcoal-700">
            Account: {enrollment.accountName} · Issuer: {enrollment.issuer}
          </p>
        </div>
        {alert}
        <Field label="Authenticator code" required error={fieldError}>
          <Input
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            value={code}
            onChange={(e) => setCode(e.target.value)}
          />
        </Field>
        <Button type="submit" loading={pending} className="w-full">
          Confirm and continue
        </Button>
      </form>
    );
  }

  return (
    <div className="space-y-4">
      <Alert tone="warning" title="Save your recovery codes now">
        <p>
          These 10 codes are shown only once. Each works one time if you lose your authenticator. Store them
          somewhere safe and private.
        </p>
      </Alert>
      <ul aria-label="Recovery codes" className="grid grid-cols-2 gap-2 rounded-md bg-warm-100 p-4 font-mono text-sm">
        {codes.map((c) => (
          <li key={c}>{c}</li>
        ))}
      </ul>
      <div className="flex flex-wrap gap-2">
        <CopyButton text={codes.join('\n')} label="Copy codes" />
        <Button variant="secondary" onClick={() => downloadCodes(codes)}>
          Download codes
        </Button>
      </div>
      <label className="flex min-h-11 cursor-pointer items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={acknowledged}
          onChange={(e) => setAcknowledged(e.target.checked)}
          className="size-4 accent-navy-900"
        />
        I have saved these recovery codes
      </label>
      <Button onClick={finish} disabled={!acknowledged}>
        Continue
      </Button>
    </div>
  );
}

function CopyButton({ text, label }: { text: string; label: string }) {
  const [state, setState] = useState<'idle' | 'copied' | 'failed'>('idle');
  async function copy() {
    try {
      await navigator.clipboard.writeText(text);
      setState('copied');
    } catch {
      setState('failed');
    }
  }
  return (
    <>
      <Button variant="secondary" onClick={copy}>
        {label}
      </Button>
      <span role="status" className="text-xs text-charcoal-700">
        {state === 'copied' ? 'Copied' : state === 'failed' ? 'Copy failed. Select the text instead.' : ''}
      </span>
    </>
  );
}

function downloadCodes(codes: string[]) {
  const body = `KYRO admin recovery codes (each works once)\n\n${codes.join('\n')}\n`;
  const url = URL.createObjectURL(new Blob([body], { type: 'text/plain' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = 'kyro-recovery-codes.txt';
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
