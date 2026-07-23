import {
  CheckIcon,
  FloppyDiskIcon,
  MagnifyingGlassIcon,
  XIcon,
} from "@phosphor-icons/react";
import { useMemo, useState } from "react";
import {
  useCatalogPage,
  useHomepageSettings,
  useUpdateHomepageSettings,
} from "../hooks";
import type {
  HomepageSettings,
  HomepageSettingsUpdate,
  PackageKind,
  PackageSummary,
} from "../types";
import { useRegistry } from "../use-registry";
import { PackageIcon } from "./PackageIcon";
import { StatePanel } from "./StatePanel";

const MAX_FEATURED_PACKAGES = 6;

export function HomepageSettingsPanel() {
  const { session } = useRegistry();
  const settings = useHomepageSettings();
  const providers = useCatalogPage({
    kind: "provider",
    sort: "name",
    limit: 100,
  });
  const modules = useCatalogPage({
    kind: "module",
    sort: "name",
    limit: 100,
  });
  const update = useUpdateHomepageSettings(session.csrfToken);

  if (settings.isPending || providers.isPending || modules.isPending) {
    return <StatePanel kind="loading" />;
  }
  if (settings.isError || providers.isError || modules.isError) {
    return (
      <StatePanel
        kind="api-error"
        action={() => {
          void settings.refetch();
          void providers.refetch();
          void modules.refetch();
        }}
      />
    );
  }

  return (
    <HomepageSettingsForm
      key={settings.data.updatedAt}
      initialSettings={settings.data}
      providers={providers.data.items}
      modules={modules.data.items}
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
  modules,
  error,
  saving,
  saved,
  onSave,
}: {
  initialSettings: HomepageSettings;
  providers: PackageSummary[];
  modules: PackageSummary[];
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
    featuredModuleIds: initialSettings.featuredModuleIds,
  }));

  const toggleFeatured = (
    field: "featuredProviderIds" | "featuredModuleIds",
    packageId: string,
  ) => {
    setForm((current) => {
      const selected = current[field];
      return {
        ...current,
        [field]: selected.includes(packageId)
          ? selected.filter((value) => value !== packageId)
          : [...selected, packageId],
      };
    });
  };

  return (
    <form
      className="admin-homepage-form admin-panel-form"
      onSubmit={(event) => {
        event.preventDefault();
        void onSave(form);
      }}
    >
      <div className="admin-section-toolbar">
        <div>
          <h2>Homepage experience</h2>
          <p>
            Manage the announcement and the provider and module collections
            promoted to signed-in users.
          </p>
        </div>
      </div>

      <section>
        <div className="admin-form-section-heading">
          <div>
            <h2>Registry announcement</h2>
            <p>Publish a concise notice below the global search bar.</p>
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
            <span>{form.notificationEnabled ? "Visible" : "Hidden"}</span>
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
              rows={2}
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

      <section className="admin-curation-section">
        <div className="admin-form-section-heading">
          <div>
            <h2>Featured catalog</h2>
            <p>
              Search the complete catalog and select up to six of each package
              type. Users only see packages they are entitled to access.
            </p>
          </div>
        </div>
        <div className="admin-feature-picker-grid">
          <FeaturedPackagePicker
            title="Featured providers"
            kind="provider"
            items={providers}
            selectedIds={form.featuredProviderIds}
            onToggle={(packageId) => {
              toggleFeatured("featuredProviderIds", packageId);
            }}
          />
          <FeaturedPackagePicker
            title="Featured modules"
            kind="module"
            items={modules}
            selectedIds={form.featuredModuleIds}
            onToggle={(packageId) => {
              toggleFeatured("featuredModuleIds", packageId);
            }}
          />
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
          {saving ? "Saving…" : "Save changes"}
        </button>
      </div>
    </form>
  );
}

function FeaturedPackagePicker({
  title,
  kind,
  items,
  selectedIds,
  onToggle,
}: {
  title: string;
  kind: PackageKind;
  items: PackageSummary[];
  selectedIds: string[];
  onToggle: (packageId: string) => void;
}) {
  const [query, setQuery] = useState("");
  const byId = useMemo(
    () => new Map(items.map((item) => [packageId(item), item])),
    [items],
  );
  const matchingItems = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) return items;
    return items.filter((item) => {
      const qualifiedName = [item.namespace, item.name, item.target]
        .filter((value): value is string => value !== undefined)
        .join("/");
      return [
        qualifiedName,
        packageId(item),
        item.namespace,
        item.name,
        item.target,
        item.provider,
        item.description,
      ]
        .filter((value): value is string => value !== undefined)
        .some((value) => value.toLowerCase().includes(normalized));
    });
  }, [items, query]);

  return (
    <section className="admin-feature-picker" aria-label={title}>
      <header>
        <div>
          <h3>{title}</h3>
          <span>
            {selectedIds.length}/{MAX_FEATURED_PACKAGES} selected
          </span>
        </div>
        <label className="admin-package-search">
          <MagnifyingGlassIcon size={16} aria-hidden="true" />
          <span className="sr-only">Search {kind}s</span>
          <input
            type="search"
            value={query}
            placeholder={`Search ${kind}s`}
            onChange={(event) => {
              setQuery(event.target.value);
            }}
          />
          {query ? (
            <button
              type="button"
              aria-label={`Clear ${kind} search`}
              onClick={() => {
                setQuery("");
              }}
            >
              <XIcon size={14} />
            </button>
          ) : null}
        </label>
      </header>

      <div className="admin-selected-packages" aria-label={`Selected ${kind}s`}>
        {selectedIds.length === 0 ? (
          <p>No {kind}s selected.</p>
        ) : (
          selectedIds.map((id) => {
            const item = byId.get(id);
            return (
              <span key={id}>
                {item === undefined
                  ? id
                  : `${item.namespace}/${item.name}${
                      item.target === undefined ? "" : `/${item.target}`
                    }`}
                <button
                  type="button"
                  aria-label={`Remove ${item?.name ?? id}`}
                  onClick={() => {
                    onToggle(id);
                  }}
                >
                  <XIcon size={13} />
                </button>
              </span>
            );
          })
        )}
      </div>

      <div className="admin-package-results">
        {matchingItems.length === 0 ? (
          <p className="admin-package-empty">No matching {kind}s.</p>
        ) : (
          matchingItems.map((item) => {
            const id = packageId(item);
            const selected = selectedIds.includes(id);
            return (
              <button
                type="button"
                className={selected ? "selected" : ""}
                aria-pressed={selected}
                disabled={
                  !selected && selectedIds.length >= MAX_FEATURED_PACKAGES
                }
                key={id}
                onClick={() => {
                  onToggle(id);
                }}
              >
                <PackageIcon
                  kind={item.kind}
                  name={item.kind === "module" ? item.provider : item.name}
                  size="small"
                />
                <span>
                  <strong>
                    {item.namespace}/{item.name}
                  </strong>
                  <small>
                    {item.kind === "module"
                      ? `${item.target ?? item.provider} provider`
                      : item.description}
                  </small>
                </span>
                {selected ? <CheckIcon size={16} weight="bold" /> : null}
              </button>
            );
          })
        )}
      </div>
    </section>
  );
}

function packageId(item: PackageSummary): string {
  return [
    item.kind,
    item.namespace,
    item.name,
    ...(item.target === undefined ? [] : [item.target]),
  ].join("/");
}
