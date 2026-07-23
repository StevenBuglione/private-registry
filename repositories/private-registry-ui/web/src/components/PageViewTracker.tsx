import { useEffect, useRef } from "react";
import { useLocation } from "react-router";
import { recordPageView } from "../api/analytics";
import { useRegistry } from "../use-registry";

export function PageViewTracker() {
  const location = useLocation();
  const { session } = useRegistry();
  const lastRecordedPath = useRef<string | null>(null);

  useEffect(() => {
    if (lastRecordedPath.current === location.pathname) return;
    lastRecordedPath.current = location.pathname;
    void recordPageView(location.pathname, session.csrfToken).catch(() => {
      // Analytics must never interrupt Registry navigation.
    });
  }, [location.pathname, session.csrfToken]);

  return null;
}
