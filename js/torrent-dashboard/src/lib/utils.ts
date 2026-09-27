import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

// /host/C/Movies -> C:\Movies ; /app/downloads -> friendly default label
export function friendlyPath(p: string | null): string {
  if (!p) return 'Default downloads'
  if (p === '/app/downloads' || p.endsWith('/app/downloads')) return 'Default downloads'
  return p.replace(/^\/host\/([A-Za-z])/, '$1:').replace(/\//g, '\\')
}
