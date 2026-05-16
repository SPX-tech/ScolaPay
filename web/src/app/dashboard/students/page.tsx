'use client';

import { useState, useEffect } from 'react';
import { studentsApi, classesApi } from '@/lib/api';
import { formatCurrency, getStatusColor, cn } from '@/lib/utils';
import {
  BookOpen,
  Loader2,
  Plus,
  Search,
  ChevronLeft,
  ChevronRight,
  Download,
  X,
  Filter,
} from 'lucide-react';

type Student = {
  id: string;
  studentNumber: string;
  fullName: string;
  guardianPhone?: string;
  guardianName?: string;
  status: string;
  debtCents: string;
  creditCents: string;
  class?: { id: string; name: string; academicYear: string };
  createdAt: string;
};

export default function StudentsPage() {
  const [students, setStudents] = useState<Student[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [total, setTotal] = useState(0);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showImportModal, setShowImportModal] = useState(false);
  const limit = 20;

  const fetchStudents = async () => {
    setLoading(true);
    try {
      const res = await studentsApi.list({ page, limit, search: search || undefined, status: statusFilter || undefined });
      setStudents(res.data.data);
      setTotalPages(res.data.meta.totalPages);
      setTotal(res.data.meta.total);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchStudents(); }, [page, statusFilter]);

  const handleSearch = () => {
    setPage(1);
    fetchStudents();
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Étudiants</h1>
          <p className="text-muted-foreground mt-1">{total} élèves inscrits</p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => setShowImportModal(true)}
            className="px-4 py-2.5 rounded-lg border border-input text-foreground hover:bg-secondary transition-colors text-sm flex items-center gap-2"
          >
            <Download className="w-4 h-4" />
            Importer CSV
          </button>
          <button
            onClick={() => setShowCreateModal(true)}
            className="px-4 py-2.5 rounded-lg bg-primary text-white hover:bg-primary/90 transition-colors text-sm flex items-center gap-2"
          >
            <Plus className="w-4 h-4" />
            Ajouter
          </button>
        </div>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-3 mb-4">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            placeholder="Rechercher par nom ou matricule..."
            className="w-full pl-9 pr-4 py-2 text-sm rounded-lg border border-input bg-background focus:outline-none focus:ring-2 focus:ring-ring"
          />
        </div>
        <select
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value); setPage(1); }}
          className="px-3 py-2 text-sm rounded-lg border border-input bg-background focus:outline-none focus:ring-2 focus:ring-ring"
        >
          <option value="">Tous les statuts</option>
          <option value="ACTIVE">Actifs</option>
          <option value="SUSPENDED">Suspendus</option>
          <option value="GRADUATED">Diplômés</option>
        </select>
      </div>

      {/* Table */}
      <div className="bg-card border rounded-xl overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b bg-secondary/50">
                <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Matricule</th>
                <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Nom complet</th>
                <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Classe</th>
                <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Téléphone</th>
                <th className="text-right px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Dette</th>
                <th className="text-center px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Statut</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={6} className="text-center py-12"><Loader2 className="w-6 h-6 animate-spin mx-auto text-primary" /></td></tr>
              ) : students.length === 0 ? (
                <tr><td colSpan={6} className="text-center py-12 text-muted-foreground">Aucun élève trouvé</td></tr>
              ) : students.map((student) => (
                <tr key={student.id} className="border-b last:border-0 hover:bg-secondary/30 transition-colors">
                  <td className="px-4 py-3 text-sm font-mono">{student.studentNumber}</td>
                  <td className="px-4 py-3 text-sm font-medium">{student.fullName}</td>
                  <td className="px-4 py-3 text-sm text-muted-foreground">{student.class?.name || '-'}</td>
                  <td className="px-4 py-3 text-sm text-muted-foreground">{student.guardianPhone || '-'}</td>
                  <td className="px-4 py-3 text-sm text-right font-semibold">
                    <span className={Number(student.debtCents) > 0 ? 'text-amber-600' : 'text-emerald-600'}>
                      {formatCurrency(student.debtCents)}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-center">
                    <span className={cn('inline-flex px-2.5 py-0.5 rounded-full text-xs font-medium', getStatusColor(student.status))}>
                      {student.status === 'ACTIVE' ? 'Actif' : student.status === 'SUSPENDED' ? 'Suspendu' : 'Diplômé'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between px-4 py-3 border-t">
            <p className="text-sm text-muted-foreground">
              Page {page} sur {totalPages}
            </p>
            <div className="flex gap-1">
              <button
                onClick={() => setPage(Math.max(1, page - 1))}
                disabled={page === 1}
                className="p-2 rounded-lg hover:bg-secondary disabled:opacity-30 transition-colors"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                onClick={() => setPage(Math.min(totalPages, page + 1))}
                disabled={page === totalPages}
                className="p-2 rounded-lg hover:bg-secondary disabled:opacity-30 transition-colors"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
