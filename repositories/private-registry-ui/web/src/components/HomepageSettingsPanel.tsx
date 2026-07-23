import { CheckIcon, FloppyDiskIcon } from "@phosphor-icons/react";
import { useState } from "react";
import {
  useCatalogPage,
  useHomepageSettings,
  useUpdateHomepageSettings,
} from "../hooks";
import type {
  HomepageSettings,
  HomepageSettingsUpdate,
  PackageSummary,
} from "../types";
import { useRegistry } from "../use-registry";
import { PackageIcon } from "./PackageIcon";
import { StatePanel } from "./StatePanel";

export function HomepageSettingsPanel() {
  const { session } = useRegistry();
  const settings = useHomepageSettings();
  const providers = useCatalogPage({
    kind: "provider",
    sort: "name",
    limit: 100,
  });
  const update = useUpdateHomepageSettings(session.csrfToken);

  if (settings.isPending || providers.isPending) {
    return <StatePanel kind="loading" />;
  }
  if (settings.isError || providers.isError) {
    return (
      <StatePanel
        kind="api-error"
        action={() => {
          void settings.refetch();
          void providers.refetch();
        }}
      />
    );
  }

  return (
    <HomepageSettingsForm
      key={settings.data.updatedAt}
      initialSettings={settings.data}
      providers={providers.data.items}
      error={update.error?.message}
      saving={update.isPending}
      saved={update.isSuccess}
      onSave={async (value) => {
        await update.mutateAsync(value);
      }}
    />
  );
}

function HomepageSettingsForm({
  initialSettings,
  providers,
  error,
  saving,
  saved,
  onSave,
}: {
  initialSettings: HomepageSettings;
  providers: PackageSummary[];
  error: string | undefined;
  saving: boolean;
  saved: boolean;
  onSave: (value: HomepageSettingsUpdate) => Promise<void>;
}) {
  const [form, setForm] = useState<HomepageSettingsUpdate>(() => ({
    notificationEnabled: initialSettings.notificationEnabled,
    notificationTitle: initialSettings.notificationTitle,
    notificationMessage: initialSettings.notificationMessage,
    notificationLinkLabel: initialSettings.notificationLinkLabel,
    notificationLinkUrl: initialSettings.notificationLinkUrl,
    featuredProviderIds: initialSettings.featuredProviderIds,
  }));

  const toggleProvider = (providerId: string) => {
    setForm((current) => ({
      ...current,
      featuredProviderIds: current.featuredProviderIds.includes(providerId)
        ? current.featuredProviderIds.filter((value) => value !== providerId)
        : [...current.featuredProviderIds, providerId],
    }));
  };

  return (
    <form
      className="admin-homepage-form admin-panel-form"
      onSubmit={(event) => {
        event.preventDefault();
        void onSave(form);
      }}
    >
      <section>
        <div className="admin-form-section-heading">
          <div>
            <h2>Homepage notification</h2>
            <p>Publish an operational notice below the global search bar.</p>
          </div>
          <label className="admin-switch">
            <input
              type="checkbox"
              checked={form.notificationEnabled}
              onChange={(event) => {
                setForm((current) => ({
                  ...current,
                  notificationEnabled: event.target.checked,
                }));
              }}
            />
            <span>{form.notificationEnabled ? "Enabled" : "Hidden"}</span>
          </label>
        </div>
        <div className="admin-form-grid">
          <label className="admin-field wide">
            <span>Title</span>
            <input
              maxLength={120}
              required
              value={form.notificationTitle}
              onChange={(event) => {
                setForm((current) => ({
                  ...current,
                  notificationTitle: event.target.value,
                }));
              }}
            />
          </label>
          <label className="admin-field wide">
            <span>Message</span>
            <textarea
              maxLength={600}
              required
              rows={3}
              value={form.notificationMessage}
              onChange={(event) => {
                setForm((current) => ({
                  ...current,
                  notificationMessage: event.target.value,
                }));
              }}
            />
          </label>
          <label className="admin-field">
            <span>Link label (optional)</span>
            <input
              maxLength={80}
              value={form.notificationLinkLabel ?? ""}
              onChange={(event) => {
                setForm((current) => ({
                  ...current,
                  notificationLinkLabel: event.target.value || undefined,
                }));
              }}
            />
          </label>
          <label className="admin-field">
            <span>HTTPS or Registry-relative URL</span>
            <input
              maxLength={500}
              value={form.notificationLinkUrl ?? ""}
              onChange={(event) => {
                setForm((current) => ({
                  ...current,
                  notificationLinkUrl: event.target.value || undefined,
                }));
              }}
            />
          </label>
        </div>
      </section>

      <section>
        <div className="admin-form-section-heading">
          <div>
            <h2>Featured providers</h2>
            <p>Select up to six providers for the homepage.</p>
          </div>
          <strong>{form.featuredProviderIds.length}/6 selected</strong>
        </div>
        <div className="admin-provider-grid">
          {providers.map((provider) => {
            const providerId = `provider/${provider.namespace}/${provider.name}`;
            const selected = form.featuredProviderIds.includes(providerId);
            return (
              <label
                className={
                  selected ? "admin-provider selected" : "admin-provider"
                }
                key={providerId}
              >
                <input
                  type="checkbox"
                  checked={selected}
                  disabled={!selected && form.featuredProviderIds.length >= 6}
                  onChange={() => {
                    toggleProvider(providerId);
                  }}
                />
                <PackageIcon
                  kind="provider"
                  name={provider.name}
                  size="small"
                />
                <span>
                  <strong>{provider.name}</strong>
                  <small>{provider.namespace}</small>
                </span>
                {selected ? <CheckIcon size={16} weight="bold" /> : null}
              </label>
            );
          })}
        </div>
      </section>

      <div className="admin-panel-actions">
        <span
          className={
            error === undefined ? "save-confirmation" : "admin-form-error"
          }
        >
          {error ?? (saved ? "Homepage configuration saved." : "")}
        </span>
        <button
          className="admin-primary-button"
          type="submit"
          disabled={saving}
        >
          <FloppyDiskIcon size={17} />
          {saving ? "Saving…" : "Save homepage"}
        </button>
      </div>
    </form>
  );
}
