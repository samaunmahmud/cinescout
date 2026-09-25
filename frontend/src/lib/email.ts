/**
 * Close to the server's @Email check: something@something, no spaces. The server has the final say; this only
 * catches typos before a round trip.
 */
export function looksLikeEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+$/.test(value)
}

/**
 * Opens the user's email app with the draft filled in. Spaces are %20 rather than "+", which mail apps would
 * show literally; line breaks survive as %0D%0A.
 */
export function mailtoLink({ recipientEmail, subject, body }: { recipientEmail: string | null; subject: string; body: string }): string {
  const to = recipientEmail ? encodeURIComponent(recipientEmail).replace(/%40/g, '@') : ''
  const query = `subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body.replace(/\r?\n/g, '\r\n'))}`
  return `mailto:${to}?${query}`
}

/** The draft as plain text for the clipboard: the subject line, a blank line, the body. */
export function emailText({ subject, body }: { subject: string; body: string }): string {
  return `Subject: ${subject}\n\n${body}`
}
