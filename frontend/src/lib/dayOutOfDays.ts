import type { Schedule } from '../api/types'

export interface DayOutOfDays {
  /** The shoot days, earliest first. */
  days: string[]
  /** Each speaking part, in the order they first work, with the days they work on. */
  cast: { name: string; days: Set<string> }[]
}

/**
 * The production report that says which member of the cast works on which shoot day, worked out from the
 * schedule: a character works on the day their scene is listed under. Scenes without a date do not count.
 */
export function dayOutOfDays(schedule: Schedule): DayOutOfDays {
  const cast = new Map<string, Set<string>>()
  for (const day of schedule.days) {
    for (const scene of day.scenes) {
      for (const name of scene.characters) {
        if (!cast.has(name)) cast.set(name, new Set())
        cast.get(name)!.add(day.date)
      }
    }
  }
  return { days: schedule.days.map((day) => day.date), cast: [...cast].map(([name, days]) => ({ name, days })) }
}
