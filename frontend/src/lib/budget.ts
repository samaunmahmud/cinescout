import type { BudgetCategory, BudgetStatus } from '../api/types'

export const budgetCategories: BudgetCategory[] = ['VENUE', 'PERMIT', 'DEPOSIT', 'CREW', 'EQUIPMENT', 'TRAVEL', 'CATERING', 'OTHER']

export const categoryLabels: Record<BudgetCategory, string> = {
  VENUE: 'Venue',
  PERMIT: 'Permit',
  DEPOSIT: 'Deposit',
  CREW: 'Crew',
  EQUIPMENT: 'Equipment',
  TRAVEL: 'Travel',
  CATERING: 'Catering',
  OTHER: 'Other',
}

export const budgetStatuses: BudgetStatus[] = ['ESTIMATE', 'COMMITTED', 'PAID']

export const budgetStatusLabels: Record<BudgetStatus, string> = { ESTIMATE: 'Estimate', COMMITTED: 'Committed', PAID: 'Paid' }

/** "£1,250.50", in the budget's currency; an unknown code falls back to "1,250.50 XYZ". */
export function formatMoney(amount: number, currency: string): string {
  try {
    return new Intl.NumberFormat(undefined, { style: 'currency', currency, minimumFractionDigits: amount % 1 === 0 ? 0 : 2 }).format(amount)
  } catch {
    return `${amount.toLocaleString()} ${currency}`
  }
}

/**
 * What a quote as people write it comes to for the shoot: the first amount in it ("£1,200 a day" is 1200), times the
 * shoot days when it is per day. Null when the quote gives no amount.
 */
export function quoteAmount(quote: string | null, shootDays: number | null): number | null {
  if (!quote) return null
  const match = /(\d{1,3}(?:[,\s]\d{3})+|\d+)(?:\.(\d{1,2}))?/.exec(quote)
  if (!match) return null
  const amount = Number(match[1].replace(/[,\s]/g, '') + (match[2] ? `.${match[2]}` : ''))
  const perDay = /\b(a|per|\/)\s*day\b|\/\s*day|daily/i.test(quote)
  return perDay && shootDays && shootDays > 1 ? amount * shootDays : amount
}
