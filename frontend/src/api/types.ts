export type Role = 'ATTENDEE' | 'ORGANIZER' | 'ADMIN'
export type UserStatus = 'ACTIVE' | 'SUSPENDED'

export interface User {
  id: string
  name: string
  email: string
  roles: Role[]
  status: UserStatus
  createdAt: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: User
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
