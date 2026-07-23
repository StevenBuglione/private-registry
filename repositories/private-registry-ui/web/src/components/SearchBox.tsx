import { MagnifyingGlassIcon, XIcon } from "@phosphor-icons/react";
import { type FocusEvent, type SyntheticEvent, useRef, useState } from "react";
import { Link, useNavigate } from "react-router";
import { useCatalogSuggestions } from "../hooks";
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
  const inputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();
  const suggestions = useCatalogSuggestions(value);
  const providers =
    suggestions.data?.items
      .filter((item) => item.kind === "provider")
      .slice(0, 4) ?? [];
  const modules =
    suggestions.data?.items
      .filter((item) => item.kind === "module")
      .slice(0, 4) ?? [];
  const hasQuery = value.length > 0;
  const showSuggestions = open && value.trim().length >= 2;
  const suggestionsId = compact
    ? "catalog-search-compact-suggestions"
    : "catalog-search-suggestions";
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

  const clear = () => {
    setValue("");
    setOpen(false);
    inputRef.current?.focus();
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
        Search providers and modules
      </label>
      <input
        ref={inputRef}
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
      {hasQuery ? (
        <button
          type="button"
          className="search-clear-button"
          aria-label="Clear search"
          onClick={clear}
        >
          <XIcon size={16} aria-hidden="true" />
        </button>
      ) : null}
      {!compact ? (
        <button type="submit" className="button button-primary">
          Search
        </button>
      ) : (
        <kbd aria-hidden="true">/</kbd>
      )}
      {showSuggestions ? (
        <div
          id={suggestionsId}
          className="search-suggestions"
          role="region"
          aria-label="Search suggestions"
        >
          {suggestions.isPending ? (
            <div className="suggestion-status">Searching packages…</div>
          ) : null}
          {!suggestions.isPending &&
          providers.length === 0 &&
          modules.length === 0 ? (
            <div className="suggestion-status">No packages found.</div>
          ) : null}
          {providers.length ? (
            <SuggestionGroup
              title="Providers"
              items={providers}
              query={value}
              onSelect={() => {
                setOpen(false);
              }}
            />
          ) : null}
          {modules.length ? (
            <SuggestionGroup
              title="Modules"
              items={modules}
              query={value}
              onSelect={() => {
                setOpen(false);
              }}
            />
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
  onSelect,
}: {
  title: string;
  items: NonNullable<ReturnType<typeof useCatalogSuggestions>["data"]>["items"];
  query: string;
  onSelect: () => void;
}) {
  return (
    <section className="suggestion-group">
      <div>
        <strong>{title}</strong>
        <Link
          to={`/browse?q=${encodeURIComponent(query)}&kind=${title === "Providers" ? "provider" : "module"}`}
          onClick={onSelect}
        >
          See all
        </Link>
      </div>
      {items.map((item) => (
        <Link
          key={`${item.kind}-${item.namespace}-${item.name}`}
          to={packageHref(item)}
          className="suggestion-row"
          onClick={onSelect}
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
