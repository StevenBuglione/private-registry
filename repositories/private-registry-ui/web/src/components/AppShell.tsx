import {
  Menu,
  MenuButton,
  MenuItem,
  MenuItems,
  Transition,
} from "@headlessui/react";
import {
  CaretDownIcon,
  CubeIcon,
  GearIcon,
  ListIcon,
  MagnifyingGlassIcon,
  PackageIcon,
  SignOutIcon,
  UserCircleIcon,
  XIcon,
} from "@phosphor-icons/react";
import { Fragment, type ReactNode, useState } from "react";
import { Link, NavLink, Outlet } from "react-router";
import { logout } from "../api/auth";
import { ApiError } from "../api/client";
import { useSession } from "../hooks/auth";
import { useCatalogEvents } from "../hooks/catalog";
import { RegistryProvider } from "../registry-provider";
import { runtimeConfig } from "../runtime-config";
import { useRegistry } from "../use-registry";
import { LoginPage } from "./LoginPage";
import { PageViewTracker } from "./PageViewTracker";
import { RegistryBrand, RegistryMark } from "./RegistryMark";
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
    if (error instanceof ApiError && error.status === 401) {
      return (
        <LoginPage
          onSignIn={() => {
            window.location.assign("/oauth2/authorization/entra");
          }}
        />
      );
    }
    const kind =
      error instanceof ApiError && error.status === 403
        ? "revoked"
        : error instanceof ApiError &&
            (["IDENTITY_PROVIDER_UNAVAILABLE", "identity_unavailable"].includes(
              error.code ?? "",
            ) ||
              error.status === 502 ||
              error.status === 503)
          ? "identity-error"
          : "api-error";
    return (
      <PublicFrame>
        <StatePanel
          kind={kind}
          action={() => void session.refetch()}
          actionLabel="Try again"
        />
      </PublicFrame>
    );
  }

  if (!session.data.admin && session.data.apms.length === 0) {
    return (
      <RegistryProvider session={session.data}>
        <PageViewTracker />
        <PublicFrame sessionName={session.data.displayName}>
          <StatePanel kind="no-access" />
        </PublicFrame>
      </RegistryProvider>
    );
  }

  return (
    <RegistryProvider session={session.data}>
      <PageViewTracker />
      <AuthenticatedShell />
    </RegistryProvider>
  );
}

function AuthenticatedShell() {
  const [mobileOpen, setMobileOpen] = useState(false);
  useCatalogEvents();

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>
      <header className="site-header">
        <div className="header-inner">
          <NavLink to="/" className="brand-link" aria-label="Registry home">
            <RegistryBrand />
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
            <MobileAccount />
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
        <p>Terraform providers and modules for your teams.</p>
        <span className="environment-label">{runtimeConfig().environment}</span>
      </footer>
    </div>
  );
}

function HeaderActions() {
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
      window.location.assign(target);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="header-actions">
      <BrowseMenu />
      <Menu as="div" className="user-menu">
        {({ close }) => (
          <>
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
                {session.admin ? (
                  <MenuItem>
                    <Link to="/admin" onClick={close}>
                      <GearIcon size={17} /> Admin settings
                    </Link>
                  </MenuItem>
                ) : null}
                <ThemeToggle showLabel menuItem onToggle={close} />
                <MenuItem>
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => void signOut()}
                  >
                    <SignOutIcon size={17} />{" "}
                    {busy ? "Signing out…" : "Sign out"}
                  </button>
                </MenuItem>
              </MenuItems>
            </Transition>
          </>
        )}
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
              <small>Search providers and modules.</small>
            </span>
          </Link>
        </MenuItem>
      </MenuItems>
    </Menu>
  );
}

function MobileAccount() {
  const { session } = useRegistry();
  return (
    <div className="mobile-account">
      <span>
        <UserCircleIcon size={17} /> {session.displayName}
      </span>
      <small>{session.admin ? "Registry administrator" : session.email}</small>
      {session.admin ? (
        <Link className="admin-context" to="/admin">
          <GearIcon size={17} /> Admin settings
        </Link>
      ) : null}
      <ThemeToggle showLabel />
    </div>
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
            <RegistryBrand />
          </a>
          {sessionName !== undefined && sessionName.length > 0 ? (
            <span className="public-user">{sessionName}</span>
          ) : null}
        </div>
      </header>
      <main>{children}</main>
    </div>
  );
}
