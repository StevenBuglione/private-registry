import { MagnifyingGlassIcon } from "@phosphor-icons/react";
import { type FocusEvent, type SyntheticEvent, useRef, useState } from "react";
import { Link, useNavigate } from "react-router";
import { useCatalogSuggestions } from "../hooks";
import { useRegistry } from "../use-registry";
import { packageHref } from "../utils";
import { PackageIcon } from "./PackageIcon";

export function SearchBox({
  initialValue = "",
  compact = false,
  onSearch,
}: {
  initialValue?: string;
  compact?: boolean;
  onSearch?: (value: string) => void;
}) {
  const [value, setValue] = useState(initialValue);
  const [open, setOpen] = useState(false);
  const formRef = useRef<HTMLFormElement>(null);
  const navigate = useNavigate();
  const { selectedApmId } = useRegistry();
  const suggestions = useCatalogSuggestions(value, selectedApmId);
  const providers =
    suggestions.data?.items
      .filter((item) => item.kind === "provider")
      .slice(0, 4) ?? [];
  const modules =
    suggestions.data?.items
      .filter((item) => item.kind === "module")
      .slice(0, 4) ?? [];
  const submit = (event: SyntheticEvent<HTMLFormElement, SubmitEvent>) => {
    event.preventDefault();
    const query = value.trim();
    if (onSearch !== undefined) onSearch(query);
    else
      void navigate(
        query ? `/browse?q=${encodeURIComponent(query)}` : "/browse",
      );
    setOpen(false);
  };

  const closeOnBlur = (event: FocusEvent<HTMLInputElement>) => {
    if (formRef.current?.contains(event.relatedTarget) !== true) setOpen(false);
  };

  return (
    <form
      ref={formRef}
      className={compact ? "search-box compact" : "search-box"}
      role="search"
      onSubmit={submit}
    >
      <MagnifyingGlassIcon size={20} aria-hidden="true" />
      <label
        className="sr-only"
        htmlFor={compact ? "catalog-search-compact" : "catalog-search"}
      >
        Search approved providers and modules
      </label>
      <input
        id={compact ? "catalog-search-compact" : "catalog-search"}
        value={value}
        onChange={(event) => {
          setValue(event.target.value);
        }}
        placeholder="Search providers, modules, and keywords"
        autoComplete="off"
        onFocus={() => {
          setOpen(true);
        }}
        onBlur={closeOnBlur}
      />
      {!compact ? (
        <button type="submit" className="button button-primary">
          Search
        </button>
      ) : (
        <kbd aria-hidden="true">/</kbd>
      )}
      {open && value.trim().length >= 2 ? (
        <div className="search-suggestions">
          {suggestions.isPending ? (
            <div className="suggestion-status">
              Searching approved packages…
            </div>
          ) : null}
          {!suggestions.isPending &&
          providers.length === 0 &&
          modules.length === 0 ? (
            <div className="suggestion-status">No approved packages found.</div>
          ) : null}
          {providers.length ? (
            <SuggestionGroup
              title="Providers"
              items={providers}
              query={value}
            />
          ) : null}
          {modules.length ? (
            <SuggestionGroup title="Modules" items={modules} query={value} />
          ) : null}
        </div>
      ) : null}
    </form>
  );
}

function SuggestionGroup({
  title,
  items,
  query,
}: {
  title: string;
  items: NonNullable<ReturnType<typeof useCatalogSuggestions>["data"]>["items"];
  query: string;
}) {
  return (
    <section className="suggestion-group">
      <div>
        <strong>{title}</strong>
        <Link
          to={`/browse?q=${encodeURIComponent(query)}&kind=${title === "Providers" ? "provider" : "module"}`}
        >
          See all
        </Link>
      </div>
      {items.map((item) => (
        <Link
          key={`${item.kind}-${item.namespace}-${item.name}`}
          to={packageHref(item)}
          className="suggestion-row"
        >
          <PackageIcon kind={item.kind} name={item.name} size="small" />
          <span>
            <strong>
              {item.namespace} / {item.name}
            </strong>
            <small>{item.description}</small>
          </span>
          <em>{item.version}</em>
        </Link>
      ))}
    </section>
  );
}
