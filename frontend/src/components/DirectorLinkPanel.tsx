import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronDown, Clapperboard } from "lucide-react";
import { useId, useState } from "react";
import { isNotFound } from "../api/errors";
import { queryKeys } from "../api/queryKeys";
import type { DirectorScope } from "../api/types";
import { useSession } from "../auth/context";
import { useRole } from "./projectRole";
import { Button, ErrorAlert } from "./ui";

/**
 * Sending the shortlist to the director: a link without a login where they see the shortlisted venues and answer
 * each one. Folded until opened, so the link is fetched only when someone looks. Viewers see the link but cannot make
 * or withdraw one; only the owner can make one that also shows private notes, quotes and the reasons behind fit scores.
 */
export function DirectorLinkPanel({ scope }: { scope: DirectorScope }) {
  const { api } = useSession();
  const queryClient = useQueryClient();
  const role = useRole();
  const checkboxId = useId();
  const [showPrivate, setShowPrivate] = useState(false);
  const [copied, setCopied] = useState(false);
  const [open, setOpen] = useState(false);
  const key = queryKeys.directorLink(scope.kind, scope.id);
  const link = useQuery({
    queryKey: key,
    queryFn: () =>
      api.director
        .link(scope)
        .catch((e) => (isNotFound(e) ? null : Promise.reject(e))),
    enabled: open,
  });
  const share = useMutation({
    mutationFn: () => api.director.share(scope, showPrivate),
    onSuccess: (created) => {
      setCopied(false);
      queryClient.setQueryData(key, created);
    },
  });
  const stop = useMutation({
    mutationFn: () => api.director.stop(scope),
    onSuccess: () => queryClient.setQueryData(key, null),
  });
  const url = link.data
    ? `${window.location.origin}/shortlist/${link.data.token}`
    : null;
  const canShare = role !== "VIEWER";
  const what =
    scope.kind === "project"
      ? "every scene’s shortlisted venues"
      : "this scene’s shortlisted venues";

  async function copy() {
    if (!url) return;
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return (
    <section
      aria-labelledby={`${checkboxId}-heading`}
      className="space-y-3 rounded-lg border-2 border-dashed border-ink/40 bg-paper p-4"
    >
      <h3
        id={`${checkboxId}-heading`}
        className="font-display text-xl leading-none text-ink"
      >
        <button
          type="button"
          aria-expanded={open}
          aria-controls={`${checkboxId}-body`}
          onClick={() => setOpen(!open)}
          className="flex w-full items-center gap-2 rounded text-left focus-visible:ring-4 focus-visible:ring-cue/40 focus-visible:outline-none"
        >
          <Clapperboard aria-hidden className="size-4 text-cue-ink" />
          Send to the director
          <ChevronDown
            aria-hidden
            className={`ml-auto size-5 transition-transform ${open ? "rotate-180" : ""}`}
          />
        </button>
      </h3>
      {open && (
        <div id={`${checkboxId}-body`} className="space-y-3">
          <p className="text-sm text-muted">
            {url
              ? link.data?.showPrivate
                ? `Anyone with this link sees ${what}, private notes and quotes included, and can approve or pass on each.`
                : `Anyone with this link sees ${what} (no private notes or quotes) and can approve or pass on each.`
              : canShare
                ? `A link without a login that shows ${what}. The director approves, says maybe or passes on each.`
                : "There is no director link yet. An editor or the owner can make one."}
          </p>
          <ErrorAlert error={link.error ?? share.error ?? stop.error} />
          {url ? (
            <div className="flex flex-wrap items-center gap-2">
              <input
                readOnly
                aria-label="Director link"
                value={url}
                onFocus={(e) => e.target.select()}
                className="min-w-0 flex-1 rounded-lg border border-line bg-white px-3 py-2 font-mono text-xs text-ink"
              />
              <Button variant="secondary" onClick={copy}>
                {copied ? "Copied" : "Copy link"}
              </Button>
              {canShare && (
                <Button
                  variant="ghost"
                  busy={stop.isPending}
                  onClick={() => stop.mutate()}
                >
                  Stop sharing
                </Button>
              )}
            </div>
          ) : (
            !link.isPending &&
            canShare && (
              <div className="flex flex-wrap items-center justify-between gap-3">
                {role === "OWNER" ? (
                  <label
                    htmlFor={checkboxId}
                    className="flex items-center gap-2 text-sm text-graphite"
                  >
                    <input
                      id={checkboxId}
                      type="checkbox"
                      checked={showPrivate}
                      onChange={(e) => setShowPrivate(e.target.checked)}
                      className="size-4 accent-[var(--color-cue)]"
                    />
                    Also show private notes, quotes and why each venue scored as
                    it did
                  </label>
                ) : (
                  <span />
                )}
                <Button
                  variant="secondary"
                  busy={share.isPending}
                  onClick={() => share.mutate()}
                >
                  Make a director link
                </Button>
              </div>
            )
          )}
        </div>
      )}
    </section>
  );
}
