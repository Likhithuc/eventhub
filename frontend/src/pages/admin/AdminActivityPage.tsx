import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { getAuditLog } from '../../api/adminApi'
import { Alert } from '../../components/ui/Alert'
import { LoadingBlock } from '../../components/ui/Spinner'
import { AuditList } from '../../features/admin/AuditList'
import { useQuery } from '../../lib/useQuery'

export function AdminActivityPage() {
  const [params, setParams] = useSearchParams()
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0)
  const { data, error, loading } = useQuery(`audit|${page}`, () => getAuditLog(page, 25))

  return (
    <div className="card">
      <div className="card-header">
        <div>
          <h2>Activity log</h2>
          <p className="muted small" style={{ marginTop: 2 }}>Every admin decision, newest first.</p>
        </div>
      </div>
      <div className="card-body" style={{ opacity: loading && data ? 0.55 : 1 }}>
        {error && <Alert>{error}</Alert>}
        {!data && loading && <LoadingBlock />}
        {data && <AuditList entries={data.content} />}
      </div>
      {data && data.totalPages > 1 && (
        <div className="pager">
          <span className="muted small">Page {data.page + 1} of {data.totalPages}</span>
          <div className="row" style={{ gap: 8 }}>
            <button type="button" className="btn btn-secondary btn-sm" disabled={page === 0}
              onClick={() => setParams(page - 1 ? { page: String(page - 1) } : {})}>
              <ChevronLeft size={16} /> Newer
            </button>
            <button type="button" className="btn btn-secondary btn-sm" disabled={page + 1 >= data.totalPages}
              onClick={() => setParams({ page: String(page + 1) })}>
              Older <ChevronRight size={16} />
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
