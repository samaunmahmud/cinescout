import type { RecceEntry } from '../api/types'

/*
 * The questions of a tech recce, as the web app asks and shows them. The server checks the same names and ranges
 * (RecceField).
 */

export type RecceKind = 'whole' | 'decimal' | 'yesNo' | 'text' | 'choice' | 'scale'

export interface RecceQuestion {
  name: string
  label: string
  kind: RecceKind
  /** For choices: the value sent, and how it reads. */
  choices?: { value: string; label: string }[]
  min?: number
  max?: number
  /** For text: the longest answer. */
  maxLength?: number
  hint?: string
}

export interface RecceGroup {
  title: string
  questions: RecceQuestion[]
}

export const recceGroups: RecceGroup[] = [
  {
    title: 'Power',
    questions: [
      { name: 'sockets', label: 'Sockets', kind: 'whole', min: 0, max: 200, hint: 'Usable wall sockets in the shooting space.' },
      { name: 'threePhase', label: 'Three-phase supply', kind: 'yesNo' },
      { name: 'powerNotes', label: 'Power notes', kind: 'text', maxLength: 500, hint: 'Where the board is, who has the key, generator space.' },
    ],
  },
  {
    title: 'Space and access',
    questions: [
      { name: 'ceilingHeightM', label: 'Ceiling height (m)', kind: 'decimal', min: 1, max: 50 },
      { name: 'loadInRoute', label: 'Load-in route', kind: 'text', maxLength: 500, hint: 'Where the trucks stop and the way in for kit.' },
      {
        name: 'stairsOrLift',
        label: 'Stairs or lift',
        kind: 'choice',
        choices: [
          { value: 'GROUND_LEVEL', label: 'Ground level' },
          { value: 'STAIRS', label: 'Stairs only' },
          { value: 'LIFT', label: 'Lift' },
          { value: 'STAIRS_AND_LIFT', label: 'Stairs and lift' },
        ],
      },
      { name: 'stepFree', label: 'Step-free access', kind: 'yesNo' },
    ],
  },
  {
    title: 'Sound and signal',
    questions: [
      { name: 'ambientNoise', label: 'Ambient noise', kind: 'scale', min: 1, max: 5, hint: '1 is silent, 5 is too loud to record.' },
      {
        name: 'phoneSignal',
        label: 'Phone signal',
        kind: 'choice',
        choices: [
          { value: 'NONE', label: 'None' },
          { value: 'POOR', label: 'Poor' },
          { value: 'OK', label: 'OK' },
          { value: 'STRONG', label: 'Strong' },
        ],
      },
    ],
  },
  {
    title: 'For the crew',
    questions: [
      { name: 'toilets', label: 'Toilets on site', kind: 'yesNo' },
      { name: 'holdingSpace', label: 'Green room or holding space', kind: 'yesNo' },
      { name: 'notes', label: 'Anything else', kind: 'text', maxLength: 2000 },
    ],
  },
]

export const recceQuestions: RecceQuestion[] = recceGroups.flatMap((group) => group.questions)

/** An answer in words: "Yes", "3.4 m", "Lift", "2 of 5". */
export function recceAnswerText(question: RecceQuestion, value: RecceEntry['value'] | undefined): string | null {
  if (value === undefined || value === null || value === '') return null
  switch (question.kind) {
    case 'yesNo':
      return value ? 'Yes' : 'No'
    case 'decimal':
      return question.name === 'ceilingHeightM' ? `${value} m` : String(value)
    case 'choice':
      return question.choices?.find((choice) => choice.value === value)?.label ?? String(value)
    case 'scale':
      return `${value} of ${question.max}`
    default:
      return String(value)
  }
}

/** The answers that differ from what is stored, as the server takes them: a value, or null to clear. */
export function changedAnswers(stored: Record<string, RecceEntry>, draft: Record<string, string | number | boolean | null>) {
  const changes: Record<string, string | number | boolean | null> = {}
  for (const [name, value] of Object.entries(draft)) {
    const before = stored[name]?.value ?? null
    const after = value === '' ? null : value
    if (String(before) !== String(after)) changes[name] = after
  }
  return changes
}
