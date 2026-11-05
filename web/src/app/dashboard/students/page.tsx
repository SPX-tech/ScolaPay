'use client';

import { startTransition, useState, useEffect, useCallback } from 'react';
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
  Pencil,
  Trash2,
  Upload,
  AlertCircle,
  CheckCircle,
  FileText,
  GraduationCap,
} from 'lucide-react';
import Link from 'next/link';
import { useToast } from '@/hooks/use-toast';

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

type SchoolClass = {
  id: string;
  name: string;
  academicYear: string;
  _count?: { students: number };
};



// Le type du composant reste stable entre les rendus.
  const Modal = ({ open, onClose, title, children }: { open: boolean; onClose: () => void; title: string; children: React.ReactNode }) => {
    if (!open) return null;
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
        <div className="fixed inset-0 bg-black/50" onClick={onClose} />
        <div className="relative bg-card border rounded-xl shadow-xl w-full max-w-lg max-h-[90vh] overflow-y-auto animate-in">
          <div className="flex items-center justify-between p-5 border-b sticky top-0 bg-card z-10">
            <h2 className="text-lg font-semibold">{title}</h2>
            <button onClick={onClose} className="p-1.5 rounded-lg hover:bg-secondary transition-colors">
              <X className="w-5 h-5" />
            </button>
          </div>
          <div className="p-5">{children}</div>
        </div>
      </div>
    );
  };


export default function StudentsPage() {
  const [students, setStudents] = useState<Student[]>([]);
  const [classes, setClasses] = useState<SchoolClass[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [total, setTotal] = useState(0);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [showImportModal, setShowImportModal] = useState(false);
  const [editingStudent, setEditingStudent] = useState<Student | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const { toast } = useToast();
  const limit = 20;

  // Form state
  const [form, setForm] = useState({
    studentNumber: '',
    fullName: '',
    guardianPhone: '',
    guardianName: '',
    classId: '',
    initialDebtCents: '',
  });

  // Import state
  const [importText, setImportText] = useState('');
  const [importResult, setImportResult] = useState<any>(null);
  const [importLoading, setImportLoading] = useState(false);

  const fetchStudents = useCallback(async () => {
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
  }, [page, limit, search, statusFilter]);

  const fetchClasses = useCallback(async () => {
    try {
      const res = await classesApi.list();
      setClasses(res.data || []);
    } catch (err) {
      console.error(err);
    }
  }, []);

  useEffect(() => { startTransition(() => { void fetchClasses(); }); }, [fetchClasses]);
  useEffect(() => { startTransition(() => { void fetchStudents(); }); }, [fetchStudents]);

  const resetForm = () => {
    setForm({
      studentNumber: '',
      fullName: '',
      guardianPhone: '',
      guardianName: '',
      classId: '',
      initialDebtCents: '',
    });
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setActionLoading('create');
    try {
      await studentsApi.create({
        ...form,
        initialDebtCents: form.initialDebtCents ? String(Math.round(parseFloat(form.initialDebtCents) * 100)) : undefined,
        guardianPhone: form.guardianPhone || undefined,
        guardianName: form.guardianName || undefined,
        classId: form.classId || undefined,
      });
      setShowCreateModal(false);
      resetForm();
      fetchStudents();
    } catch (err: any) {
      toast({ title: 'Erreur', description: err.response?.data?.message || 'Erreur lors de la création', variant: 'error' });
    } finally {
      setActionLoading(null);
    }
  };

  const handleEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingStudent) return;
    setActionLoading('edit');
    try {
      await studentsApi.update(editingStudent.id, {
        fullName: form.fullName || undefined,
        studentNumber: form.studentNumber || undefined,
        guardianPhone: form.guardianPhone || undefined,
        guardianName: form.guardianName || undefined,
        // Les sentinelles de statut partagent ce sélecteur mais ne sont pas des classes.
        classId: form.classId && !form.classId.startsWith('__') ? form.classId : undefined,
        status: form.classId === '__GRADUATED__' ? 'GRADUATED' : form.classId === '__SUSPENDED__' ? 'SUSPENDED' : undefined,
      });
      setShowEditModal(false);
      setEditingStudent(null);
      resetForm();
      fetchStudents();
    } catch (err: any) {
      toast({ title: 'Erreur', description: err.response?.data?.message || 'Erreur lors de la modification', variant: 'error' });
    } finally {
      setActionLoading(null);
    }
  };

  const handleDelete = async (student: Student) => {
    if (!confirm(`Supprimer définitivement ${student.fullName} ? Cette action est irréversible.`)) return;
    setActionLoading(student.id);
    try {
      await studentsApi.delete(student.id);
      fetchStudents();
    } catch (err: any) {
      toast({ title: 'Erreur', description: err.response?.data?.message || 'Erreur lors de la suppression', variant: 'error' });
    } finally {
      setActionLoading(null);
    }
  };

  const openEditModal = (student: Student) => {
    setEditingStudent(student);
    setForm({
      studentNumber: student.studentNumber,
      fullName: student.fullName,
      guardianPhone: student.guardianPhone || '',
      guardianName: student.guardianName || '',
      classId: student.class?.id || '',
      initialDebtCents: '',
    });
    setShowEditModal(true);
  };

  const handleImport = async () => {
    if (!importText.trim()) return;
    setImportLoading(true);
    setImportResult(null);

    try {
      const lines = importText.trim().split('\n').filter(l => l.trim());
      const students: any[] = lines.map(line => {
        const cols = line.split('\t');
        return {
          studentNumber: cols[0]?.trim() || '',
          fullName: cols[1]?.trim() || '',
          guardianPhone: cols[2]?.trim() || undefined,
          guardianName: cols[3]?.trim() || undefined,
          className: cols[4]?.trim() || undefined,
          debtCents: cols[5] ? String(Math.round(parseFloat(cols[5]) * 100)) : undefined,
        };
      });

      const res = await studentsApi.import({ students });
      setImportResult(res.data);
    } catch (err: any) {
      setImportResult({ success: 0, errors: [{ row: 1, message: err.response?.data?.message || 'Erreur lors de l\'import' }] });
    } finally {
      setImportLoading(false);
    }
  };

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
            onClick={() => { setImportResult(null); setImportText(''); setShowImportModal(true); }}
            className="px-4 py-2.5 rounded-lg border border-input text-foreground hover:bg-secondary transition-colors text-sm flex items-center gap-2"
          >
            <Upload className="w-4 h-4" />
            Importer CSV
          </button>
          <button
            onClick={() => { resetForm(); setShowCreateModal(true); }}
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
                <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Contact</th>
                <th className="text-right px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Dette</th>
                <th className="text-center px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Statut</th>
                <th className="text-center px-4 py-3 text-xs font-medium text-muted-foreground uppercase tracking-wider w-24">Actions</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={7} className="text-center py-12"><Loader2 className="w-6 h-6 animate-spin mx-auto text-primary" /></td></tr>
              ) : students.length === 0 ? (
                <tr><td colSpan={7} className="text-center py-12 text-muted-foreground">Aucun élève trouvé</td></tr>
              ) : students.map((student) => (
                <tr key={student.id} className="border-b last:border-0 hover:bg-secondary/30 transition-colors group">
                  <td className="px-4 py-3 text-sm font-mono">
                    <Link href={`/dashboard/students/${student.id}`} className="hover:text-primary transition-colors">
                      {student.studentNumber}
                    </Link>
                  </td>
                  <td className="px-4 py-3 text-sm font-medium">
                    <Link href={`/dashboard/students/${student.id}`} className="hover:text-primary transition-colors flex items-center gap-2">
                      <GraduationCap className="w-4 h-4 text-muted-foreground" />
                      {student.fullName}
                    </Link>
                  </td>
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
                  <td className="px-4 py-3 text-center">
                    <div className="flex items-center justify-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button
                        onClick={() => openEditModal(student)}
                        disabled={actionLoading === student.id}
                        className="p-1.5 rounded-lg text-muted-foreground hover:text-primary hover:bg-primary/10 transition-colors disabled:opacity-30"
                        title="Modifier"
                      >
                        <Pencil className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => handleDelete(student)}
                        disabled={actionLoading === student.id}
                        className="p-1.5 rounded-lg text-muted-foreground hover:text-destructive hover:bg-destructive/10 transition-colors disabled:opacity-30"
                        title="Supprimer"
                      >
                        {actionLoading === student.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <Trash2 className="w-4 h-4" />}
                      </button>
                    </div>
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

      {/* Create Modal */}
      <Modal open={showCreateModal} onClose={() => setShowCreateModal(false)} title="Nouvel étudiant">
        <form onSubmit={handleCreate} className="space-y-4">
          <div>
            <label className="block text-sm font-medium mb-1">Matricule *</label>
            <input type="text" value={form.studentNumber} onChange={(e) => setForm({ ...form, studentNumber: e.target.value })}
              required minLength={3} maxLength={64} placeholder="Ex: ETU001"
              className="w-full px-3 py-2 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring" />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">Nom complet *</label>
            <input type="text" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })}
              required minLength={3} placeholder="Ex: Jean Dupont"
              className="w-full px-3 py-2 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium mb-1">Téléphone parent</label>
              <input type="tel" value={form.guardianPhone} onChange={(e) => setForm({ ...form, guardianPhone: e.target.value })}
                placeholder="+235 XX XX XX XX"
                className="w-full px-3 py-2 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring" />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Nom du parent</label>
              <input type="text" value={form.guardianName} onChange={(e) => setForm({ ...form, guardianName: e.target.value })}
                placeholder="Ex: Pierre Dupont"
                className="w-full px-3 py-2 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring" />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium mb-1">Classe</label>
              <select value={form.classId} onChange={(e) => setForm({ ...form, classId: e.target.value })}
                className="w-full px-3 py-2 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring">
                <option value="">Sélectionner une classe</option>
                {classes.map((c) => (
                  <option key={c.id} value={c.id}>{c.name} ({c.academicYear})</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Dette initiale (FCFA)</label>
              <input type="number" value={form.initialDebtCents} onChange={(e) => setForm({ ...form, initialDebtCents: e.target.value })}
                min="0" placeholder="0"
                className="w-full px-3 py-2 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring" />
            </div>
          </div>
          <div className="flex gap-2 pt-2">
            <button type="button" onClick={() => setShowCreateModal(false)}
              className="px-4 py-2 rounded-lg border border-input text-foreground hover:bg-secondary transition-colors text-sm">Annuler</button>
            <button type="submit" disabled={actionLoading === 'create'}
              className="flex-1 px-4 py-2 rounded-lg bg-primary text-white hover:bg-primary/90 transition-colors text-sm disabled:opacity-50 flex items-center justify-center gap-2">
              {actionLoading === 'create' ? <><Loader2 className="w-4 h-4 animate-spin" /> Création...</> : 'Créer l\'étudiant'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Edit Modal */}
      <Modal open={showEditModal} onClose={() => { setShowEditModal(false); setEditingStudent(null); }} title="Modifier l'étudiant">
        <form onSubmit={handleEdit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium mb-1">Matricule</label>
            <input type="text" value={form.studentNumber} onChange={(e) => setForm({ ...form, studentNumber: e.target.value })}
              minLength={3} maxLength={64}
              className="w-full px-3 py-2 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring" />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">Nom complet</label>
            <input type="text" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })}
              minLength={3}
              className="w-full px-3 py-2 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium mb-1">Téléphone parent</label>
              <input type="tel" value={form.guardianPhone} onChange={(e) => setForm({ ...form, guardianPhone: e.target.value })}
                placeholder="+235 XX XX XX XX"
                className="w-full px-3 py-2 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring" />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Nom du parent</label>
              <input type="text" value={form.guardianName} onChange={(e) => setForm({ ...form, guardianName: e.target.value })}
                className="w-full px-3 py-2 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring" />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">Classe / Statut</label>
            <select value={form.classId} onChange={(e) => setForm({ ...form, classId: e.target.value })}
              className="w-full px-3 py-2 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring">
              <option value="">Sélectionner une classe</option>
              {classes.map((c) => (
                <option key={c.id} value={c.id}>{c.name} ({c.academicYear})</option>
              ))}
              <option value="__GRADUATED__">--- Diplômé ---</option>
              <option value="__SUSPENDED__">--- Suspendu ---</option>
            </select>
          </div>
          {editingStudent && (
            <div className="bg-secondary/50 rounded-lg p-3 text-sm space-y-1">
              <div className="flex justify-between"><span className="text-muted-foreground">Dette actuelle</span><span className={Number(editingStudent.debtCents) > 0 ? 'text-amber-600 font-semibold' : 'text-emerald-600 font-semibold'}>{formatCurrency(editingStudent.debtCents)}</span></div>
              <div className="flex justify-between"><span className="text-muted-foreground">Crédit</span><span className="font-semibold">{formatCurrency(editingStudent.creditCents)}</span></div>
            </div>
          )}
          <div className="flex gap-2 pt-2">
            <button type="button" onClick={() => { setShowEditModal(false); setEditingStudent(null); }}
              className="px-4 py-2 rounded-lg border border-input text-foreground hover:bg-secondary transition-colors text-sm">Annuler</button>
            <button type="submit" disabled={actionLoading === 'edit'}
              className="flex-1 px-4 py-2 rounded-lg bg-primary text-white hover:bg-primary/90 transition-colors text-sm disabled:opacity-50 flex items-center justify-center gap-2">
              {actionLoading === 'edit' ? <><Loader2 className="w-4 h-4 animate-spin" /> Enregistrement...</> : 'Enregistrer'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Import Modal */}
      <Modal open={showImportModal} onClose={() => { setShowImportModal(false); setImportResult(null); }} title="Import CSV d'étudiants">
        <div className="space-y-4">
          <div className="bg-secondary/50 rounded-lg p-4 text-sm">
            <p className="font-medium mb-2 flex items-center gap-2"><FileText className="w-4 h-4" /> Format attendu (tabulation) :</p>
            <code className="text-xs block bg-background rounded p-2 border">
              Matricule\tNom complet\tTéléphone\tParent\tClasse\tDette (FCFA)
            </code>
            <p className="text-xs text-muted-foreground mt-2">
              Collez vos données ci-dessous. Une ligne par étudiant, valeurs séparées par des tabulations.
            </p>
          </div>

          <textarea
            value={importText}
            onChange={(e) => setImportText(e.target.value)}
            placeholder={`ETU001\tJean Dupont\t+23599112233\tPierre Dupont\t6ème A\t25000\nETU002\tMarie Diallo\t+23599334455\t\t5ème B\t0`}
            rows={8}
            className="w-full px-3 py-2 rounded-lg border border-input bg-background text-sm font-mono focus:outline-none focus:ring-2 focus:ring-ring"
          />

          {importResult && (
            <div className={cn(
              'rounded-lg p-4 text-sm',
              importResult.errors?.length > 0 ? 'bg-amber-50 border border-amber-200' : 'bg-emerald-50 border border-emerald-200'
            )}>
              <div className="flex items-center gap-2 font-medium mb-2">
                {importResult.errors?.length > 0 ? (
                  <><AlertCircle className="w-4 h-4 text-amber-600" /> Import terminé avec des erreurs</>
                ) : (
                  <><CheckCircle className="w-4 h-4 text-emerald-600" /> Import réussi</>
                )}
              </div>
              <p className="text-muted-foreground">{importResult.success} étudiant(s) importé(s) avec succès.</p>
              {importResult.errors?.length > 0 && (
                <div className="mt-2 space-y-1">
                  {importResult.errors.map((err: any, i: number) => (
                    <p key={i} className="text-xs text-amber-700">Ligne {err.row}: {err.message}</p>
                  ))}
                </div>
              )}
            </div>
          )}

          <div className="flex gap-2">
            <button type="button" onClick={() => { setShowImportModal(false); setImportResult(null); }}
              className="px-4 py-2 rounded-lg border border-input text-foreground hover:bg-secondary transition-colors text-sm">Fermer</button>
            <button onClick={handleImport} disabled={importLoading || !importText.trim()}
              className="flex-1 px-4 py-2 rounded-lg bg-primary text-white hover:bg-primary/90 transition-colors text-sm disabled:opacity-50 flex items-center justify-center gap-2">
              {importLoading ? <><Loader2 className="w-4 h-4 animate-spin" /> Import...</> : 'Importer'}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
