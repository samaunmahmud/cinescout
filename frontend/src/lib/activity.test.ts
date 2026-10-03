import { describe, expect, it } from 'vitest'
import type { Activity } from '../api/types'
import { activityLine, activityText } from './activity'

const line = (overrides: Partial<Activity>): Activity => ({
  id: 'a1',
  kind: 'VENUE',
  verb: 'VENUE_STATUS_CHANGED',
  targetType: 'LOCATION',
  targetId: 'l1',
  actorId: 'u2',
  actorName: 'Grace',
  payload: {},
  createdAt: '2026-10-03T10:00:00Z',
  ...overrides,
})

describe('activity lines', () => {
  it('say what happened, linking the venue or scene it happened to', () => {
    const moved = line({ payload: { venue: 'Moonlight Diner', from: 'SUGGESTED', to: 'SHORTLISTED' } })
    expect(activityLine(moved)).toEqual(['Grace', ' moved ', { text: 'Moonlight Diner', to: '/locations/l1' }, ' from Suggested to Shortlisted'])

    expect(activityText(line({ verb: 'DIRECTOR_CALLED', actorName: 'Wes', actorId: null, payload: { venue: 'Diner', verdict: 'NO', commented: true, guest: true } })))
      .toBe('Wes (guest) passed on Diner, with a comment')
    expect(activityText(line({ verb: 'SCOUTED', targetType: 'SCENE', targetId: 's1', payload: { scene: 'Diner', added: 1 } }))).toBe('Grace scouted Diner: 1 new venue')
    expect(activityText(line({ verb: 'SCOUTED', targetType: 'SCENE', targetId: 's1', payload: { scene: 'Diner', added: 0 } }))).toBe('Grace scouted Diner: nothing new found')
    expect(activityLine(line({ verb: 'OUTREACH_STATUS_CHANGED', targetType: 'OUTREACH_DRAFT', targetId: 'd1', payload: { venue: 'Diner', locationId: 'l1', to: 'SENT' } })))
      .toContainEqual({ text: 'Diner', to: '/locations/l1?tab=outreach' })
    expect(activityText(line({ verb: 'SHOOT_DATES_CHANGED', targetType: 'SCENE', targetId: 's1', payload: { scene: 'Diner', toStart: '2026-11-02', toEnd: null } })))
      .toMatch(/^Grace set Diner to shoot from /)
    expect(activityText(line({ verb: 'SHOOT_DATES_CHANGED', targetType: 'SCENE', targetId: 's1', payload: { scene: 'Diner', toStart: null, toEnd: null } })))
      .toBe('Grace cleared the dates of Diner')
    expect(activityText(line({ verb: 'COMMENTED', payload: { venue: 'Diner', reply: true } }))).toBe('Grace replied on Diner')
  })

  it('say who came and went on the crew', () => {
    expect(activityText(line({ verb: 'MEMBER_JOINED', actorName: 'Ada', payload: { member: 'Grace', role: 'VIEWER' } }))).toBe('Ada added Grace as a viewer')
    expect(activityText(line({ verb: 'MEMBER_JOINED', payload: { member: 'Grace', role: 'EDITOR', byInvite: true } }))).toBe('Grace joined as an editor')
    expect(activityText(line({ verb: 'MEMBER_LEFT', payload: { member: 'Grace' } }))).toBe('Grace left the project')
    expect(activityText(line({ verb: 'ROLE_CHANGED', actorName: 'Ada', payload: { member: 'Grace', to: 'EDITOR' } }))).toBe('Ada made Grace an editor')
    expect(activityText(line({ verb: 'OWNERSHIP_TRANSFERRED', actorName: null, actorId: null, payload: { member: 'Grace' } })))
      .toBe('A former member handed the project to Grace')
  })
})
