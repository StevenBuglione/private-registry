import {
  Dialog,
  DialogBackdrop,
  DialogPanel,
  DialogTitle,
} from "@headlessui/react";
import { CheckIcon, XIcon } from "@phosphor-icons/react";
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

export function AdminHomepageDialog({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const { session } = useRegistry();
  const settings = useHomepageSettings();
  const providers = useCatalogPage({
    kind: "provider",
    sort: "name",
    limit: 100,
  });
  const update = useUpdateHomepageSettings(session.csrfToken);

  return (
    <Dialog open={open} onClose={onClose} className="admin-dialog">
      <DialogBackdrop className="admin-dialog-backdrop" />
      <div className="admin-dialog-positioner">
        <DialogPanel className="admin-dialog-panel">
          <div className="admin-dialog-heading">
            <div>
              <DialogTitle>Homepage settings</DialogTitle>
              <p>Manage the homepage notification and featured providers.</p>
            </div>
            <button type="button" aria-label="Close" onClick={onClose}>
              <XIcon size={18} />
            </button>
          </div>
          {settings.isPending || providers.isPending ? (
            <div className="admin-dialog-state">
              <StatePanel kind="loading" />
            </div>
          ) : settings.isError || providers.isError ? (
            <div className="admin-dialog-state">
              <StatePanel
                kind="api-error"
                action={() => {
                  void settings.refetch();
                  void providers.refetch();
                }}
              />
            </div>
          ) : (
            <AdminHomepageForm
              key={settings.data.updatedAt}
              initialSettings={settings.data}
              providers={providers.data.items}
              error={update.error?.message}
              saving={update.isPending}
              onCancel={onClose}
              onSave={async (value) => {
                await update.mutateAsync(value);
                onClose();
              }}
            />
          )}
        </DialogPanel>
      </div>
    </Dialog>
  );
}

function AdminHomepageForm({
  initialSettings,
  providers,
  error,
  saving,
  onCancel,
  onSave,
}: {
  initialSettings: HomepageSettings;
  providers: PackageSummary[];
  error: string | undefined;
  saving: boolean;
  onCancel: () => void;
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
      className="admin-homepage-form"
      onSubmit={(event) => {
        event.preventDefault();
        void onSave(form);
      }}
    >
      <section>
        <div className="admin-form-section-heading">
          <div>
            <h2>Homepage notification</h2>
            <p>Shown below global search when enabled.</p>
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

      {error !== undefined ? (
        <p className="admin-form-error" role="alert">
          {error}
        </p>
      ) : null}
      <div className="admin-dialog-actions">
        <button type="button" onClick={onCancel}>
          Cancel
        </button>
        <button className="primary" type="submit" disabled={saving}>
          {saving ? "Saving…" : "Save homepage"}
        </button>
      </div>
    </form>
  );
}
