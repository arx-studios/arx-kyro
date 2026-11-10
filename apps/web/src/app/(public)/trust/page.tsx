import type { Metadata } from 'next';

export const metadata: Metadata = { title: 'Privacy and safety' };

export default function TrustPage() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-10">
      <h1 className="text-3xl font-bold text-navy-900">Privacy and safety</h1>
      <p className="mt-3 text-charcoal-700">
        KYRO is currently a sandbox demonstration. All people, institutions and amounts are fictional and no real money
        is moved. The commitments below describe how the product is designed to behave.
      </p>

      <section id="privacy" aria-labelledby="privacy-h" className="mt-8">
        <h2 id="privacy-h" className="text-xl font-semibold text-charcoal-900">Privacy</h2>
        <ul className="mt-3 list-disc space-y-2 pl-6 text-charcoal-700">
          <li>Donors only see a public display name, never your email address or legal name.</li>
          <li>Supporting documents such as bills are visible only to the KYRO verification team and are never shown publicly.</li>
          <li>Document links are short-lived and are issued only to authorised reviewers.</li>
          <li>You can ask to withdraw a request at any time before it is funded.</li>
        </ul>
      </section>

      <section id="safety" aria-labelledby="safety-h" className="mt-8">
        <h2 id="safety-h" className="text-xl font-semibold text-charcoal-900">Safety and verification</h2>
        <ul className="mt-3 list-disc space-y-2 pl-6 text-charcoal-700">
          <li>Every request is reviewed by a person before it can be seen by donors.</li>
          <li>Altered bills must go back to review; an approved request cannot be quietly edited.</li>
          <li>Reviewers cannot review their own requests, and every decision is recorded in an audit log.</li>
          <li>KYRO does not hold pooled funds or a wallet. Payments are out of scope for this sandbox.</li>
        </ul>
      </section>

      <section id="terms" aria-labelledby="terms-h" className="mt-8">
        <h2 id="terms-h" className="text-xl font-semibold text-charcoal-900">Terms of use (summary)</h2>
        <p className="mt-3 text-charcoal-700">
          Use only truthful information and genuine documents. Requests that appear misleading or duplicated may be
          rejected, and accounts that submit false documents may be suspended. This summary is placeholder text for the
          sandbox and is not a legal agreement.
        </p>
      </section>
    </div>
  );
}
