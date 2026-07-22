export type Theme = "light" | "dark";

const storageKey = "registry-theme";

export function initializeTheme(): Theme {
  const theme = preferredTheme();
  applyTheme(theme);
  return theme;
}

export function currentTheme(): Theme {
  const theme = document.documentElement.dataset.theme;
  return theme === "dark" ? "dark" : preferredTheme();
}

export function saveTheme(theme: Theme) {
  localStorage.setItem(storageKey, theme);
  applyTheme(theme);
}

function preferredTheme(): Theme {
  const stored = localStorage.getItem(storageKey);
  if (stored === "light" || stored === "dark") return stored;
  return window.matchMedia("(prefers-color-scheme: dark)").matches
    ? "dark"
    : "light";
}

function applyTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme;
  document.documentElement.style.colorScheme = theme;
  document
    .querySelector('meta[name="theme-color"]')
    ?.setAttribute("content", theme === "dark" ? "#0d0d0f" : "#ffffff");
}
