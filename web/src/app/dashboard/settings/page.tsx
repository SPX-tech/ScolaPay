'use client';

import { useState, useEffect } from 'react';
import { tenantApi } from '@/lib/api';
import { Building2, Globe, Clock, Loader2, MapPin } from 'lucide-react';

export default function SettingsPage() {
  const [tenant, setTenant] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    tenantApi.getMe().then((res) => {
      setTenant(res.data);
      setLoading(false);
    }).catch(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    );
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-foreground">Paramètres</h1>
        <p className="text-muted-foreground mt-1">Configuration de votre établissement</p>
      </div>

      {tenant && (
        <div className="bg-card border rounded-xl p-6 max-w-lg">
          <div className="flex items-center gap-3 mb-6">
            <div className="w-12 h-12 rounded-xl bg-primary/10 flex items-center justify-center">
              <Building2 className="w-6 h-6 text-primary" />
            </div>
            <div>
              <h2 className="text-lg font-semibold">{tenant.name}</h2>
              <p className="text-sm text-muted-foreground">Code : {tenant.code}</p>
            </div>
          </div>

          <div className="space-y-4">
            <div className="flex items-center gap-3 p-3 rounded-lg bg-secondary/50">
              <Globe className="w-5 h-5 text-muted-foreground" />
              <div>
                <p className="text-sm font-medium">Monnaie</p>
                <p className="text-sm text-muted-foreground">{tenant.currency} (Franc CFA)</p>
              </div>
            </div>
            <div className="flex items-center gap-3 p-3 rounded-lg bg-secondary/50">
              <Clock className="w-5 h-5 text-muted-foreground" />
              <div>
                <p className="text-sm font-medium">Fuseau horaire</p>
                <p className="text-sm text-muted-foreground">{tenant.timezone}</p>
              </div>
            </div>
            <div className="flex items-center gap-3 p-3 rounded-lg bg-secondary/50">
              <MapPin className="w-5 h-5 text-muted-foreground" />
              <div>
                <p className="text-sm font-medium">Statut</p>
                <p className="text-sm text-muted-foreground">
                  {tenant.isActive ? 'Établissement actif ✅' : 'Établissement inactif'}
                </p>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
