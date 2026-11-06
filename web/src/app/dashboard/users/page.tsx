'use client';

import { startTransition, useState, useEffect } from 'react';
import { usersApi } from '@/lib/api';
import { formatDate, getStatusColor, cn } from '@/lib/utils';
import { Loader2, Plus, UserPlus, UserX, UserCheck } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';

export default function UsersPage() {
  const [users, setUsers] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({ email: '', fullName: '', password: '', role: 'CASHIER', phone: '' });
  const { toast } = useToast();

  const fetchUsers = async () => {
    try {
      const res = await usersApi.list();
      setUsers(res.data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { startTransition(() => { void fetchUsers(); }); }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setActionLoading('create');
    try {
      await usersApi.create(form);
      setShowCreate(false);
      setForm({ email: '', fullName: '', password: '', role: 'CASHIER', phone: '' });
      fetchUsers();
    } catch (err: any) {
      toast({ title: 'Erreur', description: err.response?.data?.message || 'Erreur lors de la création', variant: 'error' });
    } finally {
      setActionLoading(null);
    }
  };

  const handleSuspend = async (id: string) => {
    setActionLoading(id);
    try {
      await usersApi.suspend(id);
      fetchUsers();
    } catch (err: any) {
      toast({ title: 'Erreur', description: err.response?.data?.message || 'Erreur', variant: 'error' });
    }
    finally { setActionLoading(null); }
  };

  const handleActivate = async (id: string) => {
    setActionLoading(id);
    try {
      await usersApi.activate(id);
      toast({ title: 'Utilisateur activé', variant: 'success' });
      fetchUsers();
    } catch (err: any) {
      toast({ title: 'Erreur', description: err.response?.data?.message || 'Erreur', variant: 'error' });
    }
    finally { setActionLoading(null); }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Utilisateurs</h1>
          <p className="text-muted-foreground mt-1">Gestion du personnel de l&apos;école</p>
        </div>
        <button
          onClick={() => setShowCreate(!showCreate)}
          className="px-4 py-2.5 rounded-lg bg-primary text-white hover:bg-primary/90 transition-colors text-sm flex items-center gap-2"
        >
          <Plus className="w-4 h-4" />
          Ajouter
        </button>
      </div>

      {showCreate && (
        <div className="bg-card border rounded-xl p-6 mb-6">
          <h2 className="font-semibold mb-4">Nouvel utilisateur</h2>
          <form onSubmit={handleCreate} className="space-y-4 max-w-md">
            <div>
              <label className="block text-sm font-medium mb-1">Nom complet</label>
              <input type="text" value={form.fullName} onChange={(e) => setForm({...form, fullName: e.target.value})}
                required minLength={3} className="w-full px-3 py-2 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring" />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Email</label>
              <input type="email" value={form.email} onChange={(e) => setForm({...form, email: e.target.value})}
                required className="w-full px-3 py-2 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring" />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Téléphone</label>
              <input type="tel" value={form.phone} onChange={(e) => setForm({...form, phone: e.target.value})}
                className="w-full px-3 py-2 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring" />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Mot de passe</label>
              <input type="password" value={form.password} onChange={(e) => setForm({...form, password: e.target.value})}
                required minLength={8} className="w-full px-3 py-2 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring" />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Rôle</label>
              <select value={form.role} onChange={(e) => setForm({...form, role: e.target.value})}
                className="w-full px-3 py-2 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring">
                <option value="CASHIER">Caissier</option>
                <option value="ACCOUNTANT">Comptable</option>
                <option value="TENANT_ADMIN">Admin</option>
                <option value="AUDITOR">Auditeur</option>
              </select>
            </div>
            <div className="flex gap-2">
              <button type="button" onClick={() => setShowCreate(false)}
                className="px-4 py-2 rounded-lg border border-input text-foreground hover:bg-secondary transition-colors text-sm">Annuler</button>
              <button type="submit" disabled={actionLoading === 'create'}
                className="px-4 py-2 rounded-lg bg-primary text-white hover:bg-primary/90 transition-colors text-sm disabled:opacity-50 flex items-center gap-2">
                {actionLoading === 'create' ? <><Loader2 className="w-4 h-4 animate-spin" /> Création...</> : 'Créer'}
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="bg-card border rounded-xl overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b bg-secondary/50">
                <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Nom</th>
                <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Email</th>
                <th className="text-left px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Rôle</th>
                <th className="text-center px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Statut</th>
                <th className="text-right px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Dernière connexion</th>
                <th className="text-center px-4 py-3 text-xs font-medium text-muted-foreground uppercase">Actions</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={6} className="text-center py-12"><Loader2 className="w-6 h-6 animate-spin mx-auto text-primary" /></td></tr>
              ) : users.length === 0 ? (
                <tr><td colSpan={6} className="text-center py-12 text-muted-foreground">Aucun utilisateur</td></tr>
              ) : users.map((user: any) => (
                <tr key={user.id} className="border-b last:border-0 hover:bg-secondary/30 transition-colors">
                  <td className="px-4 py-3 text-sm font-medium">{user.fullName}</td>
                  <td className="px-4 py-3 text-sm text-muted-foreground">{user.email}</td>
                  <td className="px-4 py-3 text-sm">
                    <span className={cn('inline-flex px-2.5 py-0.5 rounded-full text-xs font-medium', getStatusColor(user.role))}>
                      {user.role === 'TENANT_ADMIN' ? 'Admin' : user.role === 'CASHIER' ? 'Caissier' : user.role === 'ACCOUNTANT' ? 'Comptable' : 'Auditeur'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-center">
                    <span className={cn('inline-flex px-2.5 py-0.5 rounded-full text-xs font-medium', getStatusColor(user.status))}>
                      {user.status === 'ACTIVE' ? 'Actif' : user.status === 'SUSPENDED' ? 'Suspendu' : 'Invité'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-muted-foreground text-right">
                    {user.lastLoginAt ? formatDate(user.lastLoginAt) : 'Jamais'}
                  </td>
                  <td className="px-4 py-3 text-center">
                    {user.status === 'ACTIVE' ? (
                      <button onClick={() => handleSuspend(user.id)} disabled={actionLoading === user.id}
                        className="p-1.5 rounded-lg text-muted-foreground hover:text-amber-600 hover:bg-amber-50 transition-colors disabled:opacity-30"
                        title="Suspendre">
                        {actionLoading === user.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <UserX className="w-4 h-4" />}
                      </button>
                    ) : (
                      <button onClick={() => handleActivate(user.id)} disabled={actionLoading === user.id}
                        className="p-1.5 rounded-lg text-muted-foreground hover:text-emerald-600 hover:bg-emerald-50 transition-colors disabled:opacity-30"
                        title="Activer">
                        {actionLoading === user.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <UserCheck className="w-4 h-4" />}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
