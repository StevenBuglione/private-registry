import {
  CheckCircleIcon,
  CopyIcon,
  KeyIcon,
  PlusIcon,
  ShieldCheckIcon,
  TrashIcon,
  XIcon,
} from "@phosphor-icons/react";
import { useState } from "react";
import {
  useCreateSyncCredential,
  useRevokeSyncCredential,
  useSyncCredentials,
} from "../hooks";
import type {
  CreatedSyncCredential,
  CreateSyncCredential,
  SyncCredential,
  SyncCredentialScope,
} from "../types";
import { useRegistry } from "../use-registry";
import { formatDateTime } from "../utils";
import { StatePanel } from "./StatePanel";

const initialForm: CreateSyncCredential = {
  name: "",
  scope: "module",
  expiresInDays: 90,
};

export function SyncCredentialsPanel() {
  const { session } = useRegistry();
  const credentials = useSyncCredentials();
  const create = useCreateSyncCredential(session.csrfToken);
  const revoke = useRevokeSyncCredential(session.csrfToken);
  const [showCreate, setShowCreate] = useState(false);
  const [created, setCreated] = useState<CreatedSyncCredential>();

  if (credentials.isPending) {
    return <StatePanel kind="loading" />;
  }
  if (credentials.isError) {
    return (
      <StatePanel
        kind="api-error"
        action={() => {
          void credentials.refetch();
        }}
      />
    );
  }

  const createCredential = async (value: CreateSyncCredential) => {
    const result = await create.mutateAsync(value);
    setCreated(result);
    setShowCreate(false);
  };

  return (
    <div className="admin-credentials">
      {created === undefined ? null : (
        <OneTimeSecret
          created={created}
          onClose={() => {
            setCreated(undefined);
            create.reset();
          }}
        />
      )}
      <div className="admin-section-toolbar">
        <div>
          <h2>GitHub runner sync credentials</h2>
          <p>
            Scoped credentials let trusted workflows enqueue Artifactory changes
            without granting interactive Registry access.
          </p>
        </div>
        <button
          className="admin-primary-button"
          type="button"
          onClick={() => {
            setShowCreate(true);
          }}
        >
          <PlusIcon size={17} /> Create credential
        </button>
      </div>

      {showCreate ? (
        <CreateCredentialForm
          error={create.error?.message}
          saving={create.isPending}
          onCancel={() => {
            setShowCreate(false);
          }}
          onCreate={createCredential}
        />
      ) : null}

      <div className="admin-security-note">
        <ShieldCheckIcon size={20} weight="fill" />
        <div>
          <strong>Secrets are one-time and hashed at rest</strong>
          <p>
            Tokens expire automatically, can be revoked immediately, and are
            restricted to the selected artifact type. Every use is audited.
          </p>
        </div>
      </div>

      <CredentialTable
        credentials={credentials.data}
        revoking={revoke.isPending}
        onRevoke={async (credential) => {
          const confirmed = window.confirm(
            `Revoke “${credential.name}”? GitHub workflows using it will stop immediately.`,
          );
          if (confirmed) await revoke.mutateAsync(credential.id);
        }}
      />
    </div>
  );
}

function CreateCredentialForm({
  error,
  saving,
  onCancel,
  onCreate,
}: {
  error: string | undefined;
  saving: boolean;
  onCancel: () => void;
  onCreate: (value: CreateSyncCredential) => Promise<void>;
}) {
  const [form, setForm] = useState(initialForm);
  return (
    <form
      className="credential-create-form"
      onSubmit={(event) => {
        event.preventDefault();
        void onCreate(form);
      }}
    >
      <div className="credential-create-heading">
        <div>
          <KeyIcon size={19} />
          <strong>New sync credential</strong>
        </div>
        <button type="button" aria-label="Close" onClick={onCancel}>
          <XIcon size={17} />
        </button>
      </div>
      <div className="credential-create-fields">
        <label className="admin-field">
          <span>Name</span>
          <input
            required
            minLength={3}
            maxLength={80}
            placeholder="GitHub · modules release"
            value={form.name}
            onChange={(event) => {
              setForm((current) => ({
                ...current,
                name: event.target.value,
              }));
            }}
          />
        </label>
        <label className="admin-field">
          <span>Scope</span>
          <select
            value={form.scope}
            onChange={(event) => {
              setForm((current) => ({
                ...current,
                scope: syncCredentialScope(event.target.value),
              }));
            }}
          >
            <option value="module">Modules only</option>
            <option value="provider">Providers only</option>
            <option value="all">Modules and providers</option>
          </select>
        </label>
        <label className="admin-field">
          <span>Expires after</span>
          <select
            value={form.expiresInDays}
            onChange={(event) => {
              setForm((current) => ({
                ...current,
                expiresInDays: Number(event.target.value),
              }));
            }}
          >
            <option value={30}>30 days</option>
            <option value={90}>90 days</option>
            <option value={180}>180 days</option>
            <option value={365}>1 year</option>
          </select>
        </label>
      </div>
      <div className="credential-create-actions">
        <span className="admin-form-error">{error}</span>
        <button type="button" onClick={onCancel}>
          Cancel
        </button>
        <button
          className="admin-primary-button"
          type="submit"
          disabled={saving}
        >
          {saving ? "Creating…" : "Create credential"}
        </button>
      </div>
    </form>
  );
}

function OneTimeSecret({
  created,
  onClose,
}: {
  created: CreatedSyncCredential;
  onClose: () => void;
}) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    await navigator.clipboard.writeText(created.token);
    setCopied(true);
  };
  return (
    <section className="one-time-secret" aria-labelledby="credential-created">
      <div className="one-time-secret-heading">
        <CheckCircleIcon size={22} weight="fill" />
        <div>
          <h2 id="credential-created">Credential created</h2>
          <p>Copy this token now. It cannot be retrieved again.</p>
        </div>
        <button type="button" aria-label="Dismiss" onClick={onClose}>
          <XIcon size={17} />
        </button>
      </div>
      <div className="secret-value">
        <code>{created.token}</code>
        <button type="button" onClick={() => void copy()}>
          {copied ? <CheckCircleIcon size={17} /> : <CopyIcon size={17} />}
          {copied ? "Copied" : "Copy"}
        </button>
      </div>
      <pre>
        <code>{githubExample(created.token)}</code>
      </pre>
    </section>
  );
}

function CredentialTable({
  credentials,
  revoking,
  onRevoke,
}: {
  credentials: SyncCredential[];
  revoking: boolean;
  onRevoke: (credential: SyncCredential) => Promise<void>;
}) {
  if (credentials.length === 0) {
    return (
      <div className="admin-empty">
        <KeyIcon size={25} />
        <strong>No sync credentials</strong>
        <p>Create one when a GitHub runner needs to trigger ingestion.</p>
      </div>
    );
  }
  return (
    <div className="admin-table-wrap">
      <table className="admin-table">
        <thead>
          <tr>
            <th>Name</th>
            <th>Scope</th>
            <th>Status</th>
            <th>Last used</th>
            <th>Expires</th>
            <th>
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {credentials.map((credential) => (
            <tr key={credential.id}>
              <td>
                <strong>{credential.name}</strong>
                <small>{credential.keyPrefix}…</small>
              </td>
              <td className="admin-capitalize">{credential.scope}</td>
              <td>
                <StatusPill status={credential.status} />
              </td>
              <td>
                {credential.lastUsedAt === undefined
                  ? "Never"
                  : formatDateTime(credential.lastUsedAt)}
                <small>{credential.useCount.toLocaleString()} uses</small>
              </td>
              <td>{formatDateTime(credential.expiresAt)}</td>
              <td>
                {credential.status === "active" ? (
                  <button
                    className="icon-danger-button"
                    type="button"
                    disabled={revoking}
                    aria-label={`Revoke ${credential.name}`}
                    onClick={() => void onRevoke(credential)}
                  >
                    <TrashIcon size={17} />
                  </button>
                ) : null}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function StatusPill({ status }: { status: SyncCredential["status"] }) {
  return <span className={`admin-status ${status}`}>{status}</span>;
}

function githubExample(token: string): string {
  return `curl --fail-with-body \\
  -X POST "$REGISTRY_URL/api/v1/sync/artifacts" \\
  -H "Authorization: Bearer ${token}" \\
  -H "Idempotency-Key: \${GITHUB_RUN_ID}-\${GITHUB_RUN_ATTEMPT}" \\
  -H "Content-Type: application/json" \\
  -d '{"kind":"module","repository":"iac-module-release-local","path":"namespace/name/target/version.zip"}'`;
}

function syncCredentialScope(value: string): SyncCredentialScope {
  if (value === "provider" || value === "all") return value;
  return "module";
}
