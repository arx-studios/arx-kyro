# @kyro/design-system

KYRO design tokens (Tailwind CSS v4), accessible React components and money/date utilities.
Shipped as TypeScript source: consumers must add it to Next.js `transpilePackages`.
Peer dependencies: `react`, `react-dom` (19). No Next-specific code. Sandbox only: fictional data, no real money.

## Setup in an app

```css
/* globals.css */
@import 'tailwindcss';
@import '@kyro/design-system/theme.css';
@source '../../../packages/design-system/src'; /* adjust relative path so Tailwind scans component classes */
```

```ts
// next.config.ts
transpilePackages: ['@kyro/design-system', '@kyro/api-client']
```

## Public exports (`@kyro/design-system`)

| Export | Notes |
|---|---|
| `Button` | `variant`: `primary` (default) / `secondary` / `ghost` / `danger`; `size`: `md` / `lg`; `loading` (spinner, `aria-busy`, disabled). Defaults to `type="button"`. |
| `Card` | Rounded surface; forwards `div` props. |
| `Badge` | `tone`: `neutral` / `info` / `accent` / `success` / `warning` / `danger`. |
| `RequestStatusBadge` | `status`: `DRAFT \| SUBMITTED \| IN_REVIEW \| CHANGES_REQUESTED \| APPROVED \| REJECTED \| WITHDRAWN \| FUNDED \| CLOSED` (APPROVED is labelled "Verified"). |
| `CaseStatusBadge` | `status`: `OPEN \| ASSIGNED \| CLOSED`. |
| `Input`, `Textarea`, `Select` | Forward refs; `invalid` sets `aria-invalid` and error border. |
| `Field` | `label`, `hint?`, `error?`, `required?`, `id?`, `children`. Generates an id and wires `<label for>`, `aria-describedby` (hint + error), `aria-invalid`, `aria-required`. `children` is a render function `(props: FieldControlProps) => ReactNode` (spread onto the control) or a single element that is cloned with those props. The error is announced via `role="alert"`. |
| `Alert` | `tone`: `info` / `success` (role `status`), `warning` / `danger` (role `alert`); optional `title`. |
| `EmptyState` | `title`, `description?`, `action?`. |
| `Spinner` | `size`: `sm` / `md`; `label` (screen-reader text); `role="status"`; respects reduced motion. |
| `Dialog` | `open`, `onClose`, `title`, `description?`, `footer?`, `closeOnBackdrop?`. `role="dialog"` + `aria-modal`, labelled by title, focus trap, Escape closes, focus restored, body scroll locked. Rendered in a portal. |
| `SandboxBanner` | `role="note"`; optional `message`. Put at the top of AppShell. |
| `MoneyText` | `amountMinor` (integer), `currency` (ISO 4217), `locale?`. |
| `DateText` | `value` (ISO string or Date), `withTime?`, `locale?` (default `en-IN`), `timeZone?`. Renders `<time dateTime>`. |
| `formatMoney(amountMinor, currency, locale='en-IN')` | `formatMoney(1250050,'INR') === '₹12,500.50'`. Throws `RangeError` for non-integers. |
| `parseMoneyInput(str, currency)` | Returns `{ ok: true, amountMinor }` or `{ ok: false, error, message }` (`EMPTY`, `INVALID_FORMAT`, `TOO_MANY_DECIMALS`, `NEGATIVE`, `OUT_OF_RANGE`). Never NaN, no floats (BigInt string arithmetic). `parseMoneyInput('12,500.50','INR')` gives `{ ok: true, amountMinor: 1250050 }`. `,` is grouping only (en-US or en-IN), `.` is the decimal separator. |
| `minorUnitDigits(currency)` | Intl-derived ISO 4217 exponent (INR 2, JPY 0, KWD 3). |
| `cn`, `focusRing` | Class-name join helper and the shared focus-ring classes. |
| types | `ButtonProps`, `FieldProps`, `FieldControlProps`, `RequestStatus`, `CaseStatus`, `MoneyParseResult`, etc. |

Also exported as subpaths: `@kyro/design-system/money` (money utils only) and `@kyro/design-system/theme.css`.

## Tokens (`theme.css`, `@theme`)

Colors (utility classes like `bg-navy-900`, `text-charcoal-900`): `warm-50/100/200/300` (surfaces),
`charcoal-900/800/700/600` (text), `navy-950/900/800/700/100` (primary), `champagne-100/300/400/600/700` (accent),
`success-50/600/700`, `danger-50/600/700`, `warning-50/700`. Font `--font-sans` (Inter, falling back to system
fonts; the app is responsible for loading Inter, e.g. `next/font`). Radii `--radius-sm/md/lg/xl`; shadows `--shadow-card`, `--shadow-overlay`.
A `prefers-reduced-motion` reset is included in `@layer base`.

## Accessibility (WCAG 2.2 AA)

- Interactive controls are at least 44px tall (target size), have a visible focus ring (2px navy outline + champagne ring), and never rely on color alone (badges always carry text).
- Contrast ratios of the token pairs used (computed and asserted in `src/contrast.test.ts`; text needs 4.5:1, non-text 3:1):

| Foreground on background | Ratio |
|---|---|
| charcoal-900 on warm-50 (body text) | 14.89 |
| charcoal-700 on warm-50 | 9.73 |
| charcoal-600 on warm-50 (hints, placeholders) | 6.28 |
| charcoal-600 on warm-100 | 5.81 |
| charcoal-900 on white (inputs) | 15.80 |
| warm-50 on navy-900 (primary button, banner) | 15.06 |
| warm-50 on navy-800 (primary button hover) | 12.60 |
| navy-900 on warm-50 (secondary/ghost button) | 15.06 |
| navy-900 on navy-100 (info alert, secondary hover) | 13.00 |
| navy-800 on navy-100 (info badge) | 10.88 |
| white on danger-600 (danger button) | 6.57 |
| white on danger-700 (danger hover) | 8.96 |
| danger-700 on danger-50 (error alert/badge) | 7.84 |
| danger-700 on warm-50 (field error text) | 8.45 |
| danger-600 on warm-50 (required asterisk) | 6.20 |
| success-700 on success-50 (success alert/badge) | 7.28 |
| success-600 on warm-50 | 5.51 |
| warning-700 on warning-50 | 6.72 |
| champagne-700 on champagne-100 (accent badge) | 6.12 |
| champagne-300 on navy-900 ("Sandbox" label) | 9.62 |
| charcoal-600 on white (input border, non-text, needs 3) | 6.66 |
| navy-700 on warm-50 (focus outline, non-text, needs 3) | 9.56 |
| champagne-400 on navy-900 (non-text, needs 3) | 7.14 |
| champagne-400 on warm-50 (decorative ring only, always paired with the navy outline) | 2.11 |

## Scripts

`pnpm --filter @kyro/design-system typecheck | lint | test` (Vitest + Testing Library, jsdom). No build step: source is consumed directly.
