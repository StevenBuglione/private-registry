import {
  Menu,
  MenuButton,
  MenuItem,
  MenuItems,
  Transition,
} from "@headlessui/react";
import {
  BooksIcon,
  CaretDownIcon,
  CubeIcon,
  ListIcon,
  MagnifyingGlassIcon,
  PackageIcon,
  SignOutIcon,
  UserCircleIcon,
  XIcon,
} from "@phosphor-icons/react";
import { Fragment, type ReactNode, useState } from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router";
import { ApiError, logout } from "../api";
import { useCatalogEvents, useSession } from "../hooks";
import { RegistryProvider } from "../registry-provider";
import { runtimeConfig } from "../runtime-config";
import { useRegistry } from "../use-registry";
import { RegistryMark } from "./RegistryMark";
import { SearchBox } from "./SearchBox";
import { StatePanel } from "./StatePanel";
import { ThemeToggle } from "./ThemeToggle";

export function AppShell() {
  const session = useSession();
  if (session.isPending)
    return (
      <PublicFrame>
        <StatePanel kind="loading" />
      </PublicFrame>
    );

  if (session.isError) {
    const error = session.error;
    const kind =
      error instanceof ApiError && error.status === 401
        ? "expired"
        : error instanceof ApiError && error.status === 403
          ? "revoked"
          : error instanceof ApiError &&
              ([
                "IDENTITY_PROVIDER_UNAVAILABLE",
                "identity_unavailable",
              ].includes(error.code ?? "") ||
                error.status === 502 ||
                error.status === 503)
            ? "identity-error"
            : "api-error";
    return (
      <PublicFrame>
        <StatePanel
          kind={kind}
          action={
            kind === "expired"
              ? () => {
                  window.location.assign("/oauth2/authorization/entra");
                }
              : () => void session.refetch()
          }
          actionLabel={kind === "expired" ? "Sign in" : "Try again"}
        />
      </PublicFrame>
    );
  }

  if (!session.data.admin && session.data.apms.length === 0) {
    return (
      <PublicFrame sessionName={session.data.displayName}>
        <StatePanel kind="no-access" />
      </PublicFrame>
    );
  }

  return (
    <RegistryProvider session={session.data}>
      <AuthenticatedShell />
    </RegistryProvider>
  );
}

function AuthenticatedShell() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const { selectedApmId } = useRegistry();
  useCatalogEvents(selectedApmId);

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>
      <header className="site-header">
        <div className="header-inner">
          <NavLink to="/" className="brand-link" aria-label="Registry home">
            <RegistryMark />
            <span>Registry</span>
          </NavLink>
          <HeaderActions />
          <button
            className="mobile-menu-button"
            type="button"
            aria-expanded={mobileOpen}
            aria-label={mobileOpen ? "Close menu" : "Open menu"}
            onClick={() => {
              setMobileOpen((value) => !value);
            }}
          >
            <span>Menu</span>
            {mobileOpen ? <XIcon size={18} /> : <ListIcon size={18} />}
          </button>
        </div>
        {mobileOpen ? (
          <nav className="mobile-nav" aria-label="Mobile navigation">
            <NavLink
              to="/providers"
              onClick={() => {
                setMobileOpen(false);
              }}
            >
              <PackageIcon size={17} /> Providers
            </NavLink>
            <NavLink
              to="/modules"
              onClick={() => {
                setMobileOpen(false);
              }}
            >
              <CubeIcon size={17} /> Modules
            </NavLink>
            <NavLink
              to="/browse"
              onClick={() => {
                setMobileOpen(false);
              }}
            >
              <MagnifyingGlassIcon size={17} /> Browse all
            </NavLink>
            <NavLink
              to="/docs"
              onClick={() => {
                setMobileOpen(false);
              }}
            >
              <BooksIcon size={17} /> Documentation
            </NavLink>
            <AccessSelect compact />
            <ThemeToggle showLabel />
          </nav>
        ) : null}
      </header>
      <div className="global-search-row">
        <SearchBox compact />
      </div>
      <main id="main-content">
        <Outlet />
      </main>
      <footer className="site-footer">
        <div>
          <RegistryMark compact />
          <span>Registry</span>
        </div>
        <p>Approved infrastructure building blocks for your teams.</p>
        <span className="environment-label">{runtimeConfig().environment}</span>
      </footer>
    </div>
  );
}

function HeaderActions() {
  const navigate = useNavigate();
  const { session } = useRegistry();
  const [busy, setBusy] = useState(false);
  const initials = session.displayName
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();

  const signOut = async () => {
    setBusy(true);
    try {
      const target = await logout(session.csrfToken);
      window.location.assign(target ?? session.logoutUrl ?? "/");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="header-actions">
      <BrowseMenu />
      <AccessSelect />
      <ThemeToggle />
      <Menu as="div" className="user-menu">
        <MenuButton className="user-menu-button">
          <span className="avatar" aria-hidden="true">
            {initials || "U"}
          </span>
          <span className="user-name">{session.displayName}</span>
          <CaretDownIcon size={14} aria-hidden="true" />
        </MenuButton>
        <Transition
          as={Fragment}
          enter="menu-enter"
          enterFrom="menu-enter-from"
          enterTo="menu-enter-to"
          leave="menu-leave"
          leaveFrom="menu-leave-from"
          leaveTo="menu-leave-to"
        >
          <MenuItems anchor="bottom end" className="user-menu-items">
            <div className="user-menu-identity">
              <strong>{session.displayName}</strong>
              <span>{session.email}</span>
            </div>
            <MenuItem>
              <button
                type="button"
                onClick={() => {
                  void navigate("/docs#access");
                }}
              >
                <UserCircleIcon size={17} /> Access help
              </button>
            </MenuItem>
            <MenuItem>
              <button
                type="button"
                disabled={busy}
                onClick={() => void signOut()}
              >
                <SignOutIcon size={17} /> {busy ? "Signing out…" : "Sign out"}
              </button>
            </MenuItem>
          </MenuItems>
        </Transition>
      </Menu>
    </div>
  );
}

function BrowseMenu() {
  return (
    <Menu as="div" className="browse-menu">
      <MenuButton className="topbar-button">
        Browse <CaretDownIcon size={13} aria-hidden="true" />
      </MenuButton>
      <MenuItems anchor="bottom end" className="browse-menu-items">
        <div className="browse-menu-title">Browse the Registry</div>
        <MenuItem>
          <Link to="/providers">
            <PackageIcon size={19} />
            <span>
              <strong>Providers</strong>
              <small>Infrastructure and service APIs.</small>
            </span>
          </Link>
        </MenuItem>
        <MenuItem>
          <Link to="/modules">
            <CubeIcon size={19} />
            <span>
              <strong>Modules</strong>
              <small>Reusable infrastructure packages.</small>
            </span>
          </Link>
        </MenuItem>
        <MenuItem>
          <Link to="/browse">
            <MagnifyingGlassIcon size={19} />
            <span>
              <strong>Search all packages</strong>
              <small>Search across every approved artifact.</small>
            </span>
          </Link>
        </MenuItem>
        <MenuItem>
          <Link to="/docs">
            <BooksIcon size={19} />
            <span>
              <strong>Documentation</strong>
              <small>Access, governance, and usage guidance.</small>
            </span>
          </Link>
        </MenuItem>
      </MenuItems>
    </Menu>
  );
}

function AccessSelect({ compact = false }: { compact?: boolean }) {
  const { session, selectedApmId, setSelectedApmId } = useRegistry();
  if (session.admin && session.apms.length === 0) {
    return <span className="admin-context">Registry administrator</span>;
  }
  return (
    <label className={compact ? "access-select compact" : "access-select"}>
      <span>Access context</span>
      <select
        aria-label="Access context"
        value={selectedApmId ?? ""}
        onChange={(event) => {
          setSelectedApmId(event.target.value);
        }}
      >
        {session.admin ? <option value="">All approved packages</option> : null}
        {session.apms.map((apm) => (
          <option key={apm.id} value={apm.id}>
            {apm.id} · {apm.name}
          </option>
        ))}
      </select>
    </label>
  );
}

function PublicFrame({
  children,
  sessionName,
}: {
  children: ReactNode;
  sessionName?: string;
}) {
  return (
    <div className="public-frame">
      <header className="site-header">
        <div className="header-inner">
          <a href="/" className="brand-link">
            <RegistryMark />
            <span>Registry</span>
          </a>
          {sessionName !== undefined && sessionName.length > 0 ? (
            <span className="public-user">{sessionName}</span>
          ) : null}
          <ThemeToggle />
        </div>
      </header>
      <main>{children}</main>
    </div>
  );
}
