import {
  BookOpenTextIcon,
  KeyIcon,
  LightningIcon,
  ShieldCheckIcon,
} from "@phosphor-icons/react";

export function DocsPage() {
  return (
    <div className="page-shell docs-page">
      <header className="docs-hero">
        <p className="eyebrow">
          <BookOpenTextIcon size={16} /> Registry guide
        </p>
        <h1>Use approved infrastructure with confidence</h1>
        <p>
          Learn how access, package approval, versioning, and live catalog
          updates work.
        </p>
      </header>
      <div className="docs-layout">
        <nav aria-label="Documentation sections">
          <a href="#getting-started">Getting started</a>
          <a href="#access">Access and APM groups</a>
          <a href="#packages">Using packages</a>
          <a href="#updates">Catalog updates</a>
        </nav>
        <article>
          <section id="getting-started">
            <BookOpenTextIcon size={24} />
            <div>
              <h2>Getting started</h2>
              <p>
                Sign in with your Microsoft work account. Registry evaluates
                your current Entra memberships and shows only the providers and
                modules approved for your APM groups.
              </p>
            </div>
          </section>
          <section id="access">
            <KeyIcon size={24} />
            <div>
              <h2>Access and APM groups</h2>
              <p>
                Choose an access context from the header when you belong to more
                than one APM. Counts, search suggestions, package details,
                documentation, and updates all respect the selected context.
              </p>
            </div>
          </section>
          <section id="packages">
            <ShieldCheckIcon size={24} />
            <div>
              <h2>Using packages</h2>
              <p>
                Open any package to review its owner, lifecycle, approval
                status, risk classification, versions, and Artifactory source
                before copying the configuration into your project.
              </p>
            </div>
          </section>
          <section id="updates">
            <LightningIcon size={24} />
            <div>
              <h2>Catalog updates</h2>
              <p>
                Registry listens for governed Artifactory changes. When an
                approved version is published or revoked, open catalog views
                refresh automatically without exposing packages outside your
                access.
              </p>
            </div>
          </section>
        </article>
      </div>
    </div>
  );
}
