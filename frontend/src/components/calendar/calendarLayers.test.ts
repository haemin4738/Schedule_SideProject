import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DEFAULT_LAYERS, LAYERS_STORAGE_KEY, loadLayers, saveLayers } from './calendarLayers'

describe('calendarLayers', () => {
  beforeEach(() => localStorage.clear())
  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  it('loadLayers_nothingSaved_returnsAllOn', () => {
    expect(loadLayers()).toEqual({ events: true, jobApplications: true, expenses: true, specialDays: true })
  })

  it('loadLayers_savedValues_restoresThem', () => {
    localStorage.setItem(LAYERS_STORAGE_KEY, JSON.stringify({ ...DEFAULT_LAYERS, expenses: false, events: false }))
    expect(loadLayers()).toEqual({ events: false, jobApplications: true, expenses: false, specialDays: true })
  })

  it('loadLayers_partialOrWrongTypedValues_fillsWithDefaults', () => {
    localStorage.setItem(LAYERS_STORAGE_KEY, JSON.stringify({ expenses: false, events: 'no', unknown: false }))
    expect(loadLayers()).toEqual({ events: true, jobApplications: true, expenses: false, specialDays: true })
  })

  it('loadLayers_brokenJson_returnsDefaults', () => {
    localStorage.setItem(LAYERS_STORAGE_KEY, '{not json')
    expect(loadLayers()).toEqual(DEFAULT_LAYERS)
  })

  it('loadLayers_nonObjectJson_returnsDefaults', () => {
    localStorage.setItem(LAYERS_STORAGE_KEY, 'null')
    expect(loadLayers()).toEqual(DEFAULT_LAYERS)
    localStorage.setItem(LAYERS_STORAGE_KEY, '42')
    expect(loadLayers()).toEqual(DEFAULT_LAYERS)
  })

  it('loadLayers_storageThrows_returnsDefaults', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('SecurityError')
    })
    expect(loadLayers()).toEqual(DEFAULT_LAYERS)
  })

  it('saveLayers_layers_writesJsonUnderVersionedKey', () => {
    saveLayers({ ...DEFAULT_LAYERS, jobApplications: false })
    expect(JSON.parse(localStorage.getItem('lifelog.calendar.layers.v1')!)).toEqual({
      events: true,
      jobApplications: false,
      expenses: true,
      specialDays: true,
    })
  })

  it('saveLayers_storageThrows_doesNotThrow', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError')
    })
    expect(() => saveLayers(DEFAULT_LAYERS)).not.toThrow()
  })
})
