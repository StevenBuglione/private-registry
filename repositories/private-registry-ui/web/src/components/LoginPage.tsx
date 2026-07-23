import {
  ArrowRightIcon,
  CheckCircleIcon,
  LockKeyIcon,
} from "@phosphor-icons/react";
import { RegistryBrand } from "./RegistryMark";

export function LoginPage({ onSignIn }: { onSignIn: () => void }) {
  return (
    <main className="login-page">
      <section className="login-brand-panel" aria-label="Registry introduction">
        <div className="login-brand-lockup" aria-label="Oremus Labs Registry">
          <RegistryBrand />
        </div>

        <div className="login-brand-content">
          <p className="login-eyebrow">Private infrastructure catalog</p>
          <p className="login-headline">
            Trusted Terraform packages, ready for your teams.
          </p>
          <p className="login-introduction">
            Discover the providers and modules available to the applications you
            support, with access resolved from your Microsoft Entra groups.
          </p>
          <ul className="login-benefits">
            <li>
              <CheckCircleIcon size={18} weight="fill" />
              One authorized catalog for providers and modules
            </li>
            <li>
              <CheckCircleIcon size={18} weight="fill" />
              Package documentation and versions in one place
            </li>
            <li>
              <CheckCircleIcon size={18} weight="fill" />
              Access aligned to your application memberships
            </li>
          </ul>
        </div>

        <p className="login-brand-footer">Oremus Labs · Registry</p>
      </section>

      <section className="login-form-panel" aria-labelledby="login-heading">
        <div className="login-form-content">
          <p className="login-form-kicker">Oremus Labs Registry</p>
          <h1 id="login-heading">Sign in to Registry</h1>
          <p className="login-form-description">
            Use your organization’s Microsoft account to continue.
          </p>

          <button
            className="microsoft-sign-in"
            type="button"
            onClick={onSignIn}
          >
            <MicrosoftMark />
            <span>Continue with Microsoft</span>
            <ArrowRightIcon size={17} aria-hidden="true" />
          </button>

          <div className="login-security-copy">
            <LockKeyIcon size={16} aria-hidden="true" />
            <p>
              Authentication is handled by Microsoft. Registry does not receive
              or store your password.
            </p>
          </div>

          <p className="login-entitlement-copy">
            The providers and modules you can browse are determined by your
            Microsoft Entra group memberships.
          </p>
        </div>

        <footer className="login-form-footer">
          <span>Authorized access only</span>
          <span aria-hidden="true">·</span>
          <span>Protected by Microsoft Entra ID</span>
        </footer>
      </section>
    </main>
  );
}

function MicrosoftMark() {
  return (
    <span className="microsoft-mark" aria-hidden="true">
      <i />
      <i />
      <i />
      <i />
    </span>
  );
}
