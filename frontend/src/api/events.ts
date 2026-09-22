import client from './client'

export interface Event {
  id: number
  title: string
  description?: string
  startAt: string
  endAt: string
  category?: string
}

export interface CreateEventRequest {
  title: string
  description?: string
  startAt: string
  endAt: string
  category?: string
}

export const getEvents = (params?: { page?: number; size?: number }) =>
  client.get<{ success: boolean; data: Event[]; meta: unknown }>('/api/v1/events', { params })

export const getEvent = (id: number) =>
  client.get<{ success: boolean; data: Event }>(`/api/v1/events/${id}`)

export const createEvent = (body: CreateEventRequest) =>
  client.post<{ success: boolean; data: Event }>('/api/v1/events', body)

export const updateEvent = (id: number, body: Partial<CreateEventRequest>) =>
  client.put<{ success: boolean; data: Event }>(`/api/v1/events/${id}`, body)

export const deleteEvent = (id: number) =>
  client.delete(`/api/v1/events/${id}`)
