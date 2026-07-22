import { MoonIcon, SunIcon } from "@phosphor-icons/react";
import { useState } from "react";
import { currentTheme, saveTheme, type Theme } from "../theme";

export function ThemeToggle({
  showLabel = false,
  menuItem = false,
  onToggle,
}: {
  showLabel?: boolean;
  menuItem?: boolean;
  onToggle?: (() => void) | undefined;
}) {
  const [theme, setTheme] = useState<Theme>(() => currentTheme());
  const nextTheme = theme === "light" ? "dark" : "light";

  return (
    <button
      className="theme-toggle"
      type="button"
      role={menuItem ? "menuitem" : undefined}
      tabIndex={menuItem ? -1 : undefined}
      aria-label={`Switch to ${nextTheme} mode`}
      title={`Switch to ${nextTheme} mode`}
      onClick={() => {
        saveTheme(nextTheme);
        setTheme(nextTheme);
        onToggle?.();
      }}
    >
      {theme === "light" ? (
        <MoonIcon size={16} aria-hidden="true" />
      ) : (
        <SunIcon size={16} aria-hidden="true" />
      )}
      {showLabel ? <span>{capitalize(nextTheme)} mode</span> : null}
    </button>
  );
}

function capitalize(value: string) {
  return value.charAt(0).toUpperCase() + value.slice(1);
}
