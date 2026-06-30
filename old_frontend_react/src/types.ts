export interface ApiResponse<T> {
  data: T | null;
  meta: { nextCursor?: string; limit?: number } | null;
  error: { code: string; message: string; fields?: Record<string, string> } | null;
}

export interface UserSummary { id: string; email: string; displayName?: string; }
export interface AuthResponse { user: UserSummary; accessToken: string; refreshToken: string; }
