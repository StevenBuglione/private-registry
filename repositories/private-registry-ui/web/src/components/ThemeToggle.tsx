import { MoonIcon, SunIcon } from "@phosphor-icons/react";
import { useState } from "react";
import { currentTheme, saveTheme, type Theme } from "../theme";

export function ThemeToggle({ showLabel = false }: { showLabel?: boolean }) {
  const [theme, setTheme] = useState<Theme>(() => currentTheme());
  const nextTheme = theme === "light" ? "dark" : "light";

  return (
    <button
      className="theme-toggle"
      type="button"
      aria-label={`Switch to ${nextTheme} mode`}
      title={`Switch to ${nextTheme} mode`}
      onClick={() => {
        saveTheme(nextTheme);
        setTheme(nextTheme);
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
