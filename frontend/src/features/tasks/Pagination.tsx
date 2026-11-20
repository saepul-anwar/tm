import { Button } from "@/components/ui/button"
import type { PageMeta } from "@/lib/api"

type PaginationProps = { meta: PageMeta; onPageChange: (page: number) => void }

export function Pagination({ meta, onPageChange }: PaginationProps) {
  const totalPages = Math.max(1, meta.totalPages)
  const current = meta.page + 1

  return (
    <nav aria-label="Halaman" className="flex flex-wrap items-center justify-between gap-2">
      <p className="text-sm text-muted-foreground">
        Halaman {current} dari {totalPages} &middot; {meta.totalElements} tugas
      </p>
      <div className="flex gap-2">
        <Button variant="outline" disabled={meta.page <= 0} onClick={() => onPageChange(meta.page - 1)}>
          Sebelumnya
        </Button>
        <Button variant="outline" disabled={current >= totalPages} onClick={() => onPageChange(meta.page + 1)}>
          Berikutnya
        </Button>
      </div>
    </nav>
  )
}
