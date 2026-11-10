import { Card } from '@kyro/design-system';
import Link from 'next/link';

const primaryLink =
  'inline-flex min-h-11 items-center justify-center rounded-md bg-navy-900 px-6 text-base font-medium text-warm-50 hover:bg-navy-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-navy-700';
const secondaryLink =
  'inline-flex min-h-11 items-center justify-center rounded-md border border-navy-900 px-6 text-base font-medium text-navy-900 hover:bg-navy-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-navy-700';

const STEPS = [
  { title: 'Recipient submits a bill', body: 'A student or family uploads a real tuition or exam fee bill with the details of the institution.' },
  { title: 'Our team verifies it', body: 'A KYRO reviewer checks the documents and the details. Documents always stay private.' },
  { title: 'Donors see verified needs', body: 'Only requests that pass review appear for donors, with a clear Verified badge.' },
];

export default function LandingPage() {
  return (
    <>
      <section className="bg-navy-900 text-warm-50">
        <div className="mx-auto max-w-6xl px-4 py-14 sm:py-20">
          <h1 className="max-w-3xl text-4xl font-bold tracking-tight sm:text-5xl">
            Verified needs. Meaningful impact.
          </h1>
          <p className="mt-4 max-w-2xl text-lg text-warm-100">
            KYRO helps donors support education expenses that have been checked by people, not assumed. Every request
            in view has been reviewed against its supporting documents.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link href="/discover" className="inline-flex min-h-11 items-center justify-center rounded-md bg-champagne-400 px-6 text-base font-semibold text-navy-950 hover:bg-champagne-300 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white">
              Browse verified requests
            </Link>
            <Link href="/register" className="inline-flex min-h-11 items-center justify-center rounded-md border border-warm-50 px-6 text-base font-medium text-warm-50 hover:bg-navy-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white">
              Create an account
            </Link>
          </div>
        </div>
      </section>

      <section aria-labelledby="how-heading" className="mx-auto max-w-6xl px-4 py-12">
        <h2 id="how-heading" className="text-2xl font-bold text-navy-900">
          How verification works
        </h2>
        <ol className="mt-6 grid gap-4 sm:grid-cols-3">
          {STEPS.map((s, i) => (
            <li key={s.title}>
              <Card className="h-full p-5">
                <p className="text-sm font-semibold text-champagne-700">Step {i + 1}</p>
                <h3 className="mt-1 text-lg font-semibold text-charcoal-900">{s.title}</h3>
                <p className="mt-2 text-charcoal-700">{s.body}</p>
              </Card>
            </li>
          ))}
        </ol>
      </section>

      <section aria-labelledby="enter-heading" className="mx-auto max-w-6xl px-4 pb-12">
        <h2 id="enter-heading" className="text-2xl font-bold text-navy-900">
          Where do you fit?
        </h2>
        <div className="mt-6 grid gap-4 sm:grid-cols-2">
          <Card className="p-5">
            <h3 className="text-lg font-semibold text-charcoal-900">I want to give</h3>
            <p className="mt-2 text-charcoal-700">
              Browse verified education requests one at a time and tell us which ones you would like to support.
            </p>
            <div className="mt-4">
              <Link href="/register" className={primaryLink}>
                Join as a donor
              </Link>
            </div>
          </Card>
          <Card className="p-5">
            <h3 className="text-lg font-semibold text-charcoal-900">I need support</h3>
            <p className="mt-2 text-charcoal-700">
              Submit a tuition or exam fee bill for verification. You decide what is shown publicly.
            </p>
            <div className="mt-4">
              <Link href="/register" className={secondaryLink}>
                Submit a request
              </Link>
            </div>
          </Card>
        </div>
      </section>

      <section aria-labelledby="partner-heading" className="border-y border-warm-200 bg-warm-100">
        <div className="mx-auto max-w-6xl px-4 py-12">
          <h2 id="partner-heading" className="text-2xl font-bold text-navy-900">
            Institutional partnerships
          </h2>
          <p className="mt-3 max-w-3xl text-charcoal-700">
            Schools, colleges and scholarship bodies can help us confirm fee details directly. Partnership onboarding
            is planned for a later release; this sandbox does not contact any institution.
          </p>
        </div>
      </section>

      <section aria-labelledby="trust-heading" className="mx-auto max-w-6xl px-4 py-12">
        <h2 id="trust-heading" className="text-2xl font-bold text-navy-900">
          Trust and safety
        </h2>
        <ul className="mt-4 flex flex-wrap gap-x-6 gap-y-1">
          <li>
            <Link href="/trust#privacy" className="inline-flex min-h-11 items-center underline">
              How we protect your privacy
            </Link>
          </li>
          <li>
            <Link href="/trust#safety" className="inline-flex min-h-11 items-center underline">
              How we keep donors safe
            </Link>
          </li>
          <li>
            <Link href="/trust#terms" className="inline-flex min-h-11 items-center underline">
              Terms of use
            </Link>
          </li>
        </ul>
      </section>
    </>
  );
}
