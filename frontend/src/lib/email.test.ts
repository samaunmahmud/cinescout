import { describe, expect, it } from 'vitest'
import { emailText, looksLikeEmail, mailtoLink } from './email'

describe('looksLikeEmail', () => {
  it('accepts an address and catches the obvious typos', () => {
    expect(looksLikeEmail('owner@toms-diner.example')).toBe(true)
    expect(looksLikeEmail('owner@localhost')).toBe(true)
    expect(looksLikeEmail('owner.toms-diner.example')).toBe(false)
    expect(looksLikeEmail('owner @toms.example')).toBe(false)
    expect(looksLikeEmail('a@b@c')).toBe(false)
  })
})

describe('mailtoLink', () => {
  it('fills in the recipient, subject and body, keeping line breaks and special characters', () => {
    const link = mailtoLink({ recipientEmail: 'tom+bookings@diner.example', subject: 'Filming at Tom’s & co?', body: 'Hi Tom,\n\nThanks!' })
    expect(link).toBe(
      'mailto:tom%2Bbookings@diner.example?subject=Filming%20at%20Tom%E2%80%99s%20%26%20co%3F&body=Hi%20Tom%2C%0D%0A%0D%0AThanks!',
    )
    const url = new URL(link)
    expect(url.searchParams.get('subject')).toBe('Filming at Tom’s & co?')
  })

  it('leaves the recipient empty when there is none', () => {
    expect(mailtoLink({ recipientEmail: null, subject: 'S', body: 'B' })).toBe('mailto:?subject=S&body=B')
  })
})

describe('emailText', () => {
  it('puts the subject line first', () => {
    expect(emailText({ subject: 'Filming request', body: 'Hi,\nThanks' })).toBe('Subject: Filming request\n\nHi,\nThanks')
  })
})

describe('the reply address in an email link', () => {
  it('goes in Cc', () => {
    expect(mailtoLink({ recipientEmail: 'owner@diner.example', subject: 'Hi', body: 'x', replyTo: 'scout+abc@replies.example.com' })).toBe(
      'mailto:owner@diner.example?cc=scout%2Babc@replies.example.com&subject=Hi&body=x',
    )
  })
})
