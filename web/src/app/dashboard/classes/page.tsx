'use client';

import { useState, useEffect } from 'react';
import { classesApi } from '@/lib/api';
import { cn } from '@/lib/utils';
import { BookOpen, Loader2, Plus, Trash2 } from 'lucide-react';

export default function ClassesPage() {
  const [classes, setClasses] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({ name: '', description: '', academicYear: new Date().getFullYear().toString() });

  const fetchClasses = async () => {
    try {
      const res = await classesApi.list();
      setClasses(res.data);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  useEffect(() => { fetchClasses(); }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await classesApi.create(form);
      setShowCreate(false);
      setForm({ name: '', description: '', academicYear: new Date().getFullYear().toString() });
      fetchClasses();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Erreur');
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Supprimer cette classe ?')) return;
    try {
      await classesApi.delete(id);
      fetchClasses();
    } catch (err) { console.error(err); }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Classes</h1>
          <p className="text-muted-foreground mt-1">Gestion des classes et filières</p>
        </div>
        <button onClick={() => setShowCreate(!showCreate)}
          className="px-4 py-2.5 rounded-lg bg-primary text-white hover:bg-primary/90 transition-colors text-sm flex items-center gap-2">
          <Plus className="w-4 h-4" /> Nouvelle classe
        </button>
      </div>

      {showCreate && (
        <div className="bg-card border rounded-xl p-6 mb-6 max-w-md">
          <h2 className="font-semibold mb-4">Nouvelle classe</h2>
          <form onSubmit={handleCreate} className="space-y-4">
            <div>
              <label className="block text-sm font-medium mb-1">Nom</label>
              <input type="text" value={form.name} onChange={(e) => setForm({...form, name: e.target.value})}
                required minLength={2} className="w-full px-3 py-2 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring" />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Description</label>
              <input type="text" value={form.description} onChange={(e) => setForm({...form, description: e.target.value})}
                className="w-full px-3 py-2 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring" />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Année académique</label>
              <input type="text" value={form.academicYear} onChange={(e) => setForm({...form, academicYear: e.target.value})}
                required minLength={4} className="w-full px-3 py-2 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring" />
            </div>
            <div className="flex gap-2">
              <button type="button" onClick={() => setShowCreate(false)}
                className="px-4 py-2 rounded-lg border border-input text-foreground hover:bg-secondary transition-colors text-sm">Annuler</button>
              <button type="submit"
                className="px-4 py-2 rounded-lg bg-primary text-white hover:bg-primary/90 transition-colors text-sm">Créer</button>
            </div>
          </form>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {loading ? (
          <div className="col-span-full flex justify-center py-12"><Loader2 className="w-8 h-8 animate-spin text-primary" /></div>
        ) : classes.length === 0 ? (
          <p className="col-span-full text-center py-12 text-muted-foreground">Aucune classe créée</p>
        ) : classes.map((cls: any) => (
          <div key={cls.id} className="bg-card border rounded-xl p-5 hover:shadow-md transition-all group">
            <div className="flex items-start justify-between mb-3">
              <div className="flex items-center gap-3">
                <div className="p-2.5 rounded-lg bg-primary/10">
                  <BookOpen className="w-5 h-5 text-primary" />
                </div>
                <div>
                  <h3 className="font-semibold">{cls.name}</h3>
                  <p className="text-xs text-muted-foreground">{cls.academicYear}</p>
                </div>
              </div>
              <button onClick={() => handleDelete(cls.id)}
                className="p-1.5 rounded-lg opacity-0 group-hover:opacity-100 text-muted-foreground hover:text-destructive hover:bg-destructive/10 transition-all">
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
            {cls.description && (
              <p className="text-sm text-muted-foreground mb-3">{cls.description}</p>
            )}
            <div className="text-sm">
              <span className="text-muted-foreground">Élèves : </span>
              <span className="font-semibold">{cls._count?.students || 0}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
