import type { ComponentProps } from "react"
import type { FieldError } from "react-hook-form"

import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

type FormFieldProps = {
  id: string
  label: string
  error?: FieldError
} & Omit<ComponentProps<typeof Input>, "id" | "aria-invalid" | "aria-describedby">

export function FormField({ id, label, error, ...inputProps }: FormFieldProps) {
  const errorId = `${id}-error`
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        {...inputProps}
      />
      {error?.message && (
        <p id={errorId} className="text-sm text-destructive">
          {error.message}
        </p>
      )}
    </div>
  )
}
