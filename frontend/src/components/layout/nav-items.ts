import {
  CalendarDays,
  CheckCircle2,
  LayoutDashboard,
  ListTodo,
  Settings,
  type LucideIcon,
} from "lucide-react"

export type NavItem = { label: string; href: string; icon: LucideIcon }

export const NAV_ITEMS: NavItem[] = [
  { label: "Dashboard", href: "/dashboard", icon: LayoutDashboard },
  { label: "My Tasks", href: "/tasks", icon: ListTodo },
  { label: "Calendar", href: "/calendar", icon: CalendarDays },
  { label: "Completed", href: "/completed", icon: CheckCircle2 },
  { label: "Settings", href: "/settings", icon: Settings },
]
