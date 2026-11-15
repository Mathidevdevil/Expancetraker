'use client';

import React, { useState, useEffect } from 'react';
import { Modal } from '@/components/ui/modal';
import { useFinance } from '@/context/finance-context';
import { User, Mail, Sparkles, Check, AlertCircle } from 'lucide-react';

interface EditProfileModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export function EditProfileModal({ isOpen, onClose }: EditProfileModalProps) {
  const { userProfile, updateUserProfile } = useFinance();

  const [name, setName] = useState(userProfile.name);
  const [email, setEmail] = useState(userProfile.email);
  const [bio, setBio] = useState(userProfile.bio || '');
  const [isSaving, setIsSaving] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen) {
      setName(userProfile.name);
      setEmail(userProfile.email);
      setBio(userProfile.bio || '');
      setStatusMessage(null);
    }
  }, [isOpen, userProfile]);

  const initials = React.useMemo(() => {
    const trimmed = name.trim();
    if (!trimmed) return 'SW';
    return (
      trimmed
        .split(' ')
        .filter(Boolean)
        .slice(0, 2)
        .map((s) => s[0].toUpperCase())
        .join('') || 'SW'
    );
  }, [name]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;

    setIsSaving(true);
    setStatusMessage(null);
    try {
      await updateUserProfile({
        name: name.trim(),
        email: email.trim(),
        initials,
        bio: bio.trim(),
      });
      setStatusMessage('Profile updated successfully!');
      setTimeout(() => {
        onClose();
      }, 600);
    } catch (err) {
      console.error(err);
      setStatusMessage('Failed to update profile');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Edit User Profile" maxWidth="max-w-md">
      <form onSubmit={handleSubmit} className="space-y-5">
        {/* Live Avatar Preview */}
        <div className="flex flex-col items-center justify-center p-4 rounded-2xl bg-secondary/30 border border-border/50">
          <div className="relative group">
            <div className="w-20 h-20 rounded-full bg-primary/15 border-2 border-primary text-primary flex items-center justify-center font-black text-2xl shadow-inner transition-transform group-hover:scale-105">
              {initials}
            </div>
            <div className="absolute -bottom-1 -right-1 p-1 rounded-full bg-primary text-primary-foreground">
              <Sparkles className="w-3.5 h-3.5" />
            </div>
          </div>
          <p className="text-xs font-semibold text-muted-foreground mt-2.5">
            Initials preview: <span className="font-bold text-foreground">{initials}</span>
          </p>
        </div>

        {/* Full Name Input */}
        <div className="space-y-1.5">
          <label className="text-xs font-bold text-muted-foreground uppercase tracking-wider flex items-center gap-1.5">
            <User className="w-3.5 h-3.5 text-primary" />
            Full Name
          </label>
          <input
            type="text"
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Alex Morgan"
            className="w-full px-4 py-2.5 rounded-2xl bg-secondary/40 border border-border text-foreground text-sm font-medium focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-primary transition-all"
          />
        </div>

        {/* Email Address Input */}
        <div className="space-y-1.5">
          <label className="text-xs font-bold text-muted-foreground uppercase tracking-wider flex items-center gap-1.5">
            <Mail className="w-3.5 h-3.5 text-primary" />
            Email Address
          </label>
          <input
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="e.g. alex.morgan@spendwise.app"
            className="w-full px-4 py-2.5 rounded-2xl bg-secondary/40 border border-border text-foreground text-sm font-medium focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-primary transition-all"
          />
        </div>

        {/* Bio / Tagline */}
        <div className="space-y-1.5">
          <label className="text-xs font-bold text-muted-foreground uppercase tracking-wider">
            Tagline / Status
          </label>
          <input
            type="text"
            value={bio}
            onChange={(e) => setBio(e.target.value)}
            placeholder="e.g. Personal Finance & Travel Budgeter"
            className="w-full px-4 py-2.5 rounded-2xl bg-secondary/40 border border-border text-foreground text-sm font-medium focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-primary transition-all"
          />
        </div>

        {statusMessage && (
          <div
            className={`p-3 rounded-xl text-xs font-semibold flex items-center gap-2 ${
              statusMessage.includes('success')
                ? 'bg-emerald-500/10 text-emerald-500 border border-emerald-500/20'
                : 'bg-rose-500/10 text-rose-500 border border-rose-500/20'
            }`}
          >
            {statusMessage.includes('success') ? (
              <Check className="w-4 h-4 shrink-0" />
            ) : (
              <AlertCircle className="w-4 h-4 shrink-0" />
            )}
            <span>{statusMessage}</span>
          </div>
        )}

        {/* Action Buttons */}
        <div className="flex items-center justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={onClose}
            disabled={isSaving}
            className="px-4 py-2.5 rounded-xl border border-border text-muted-foreground hover:bg-secondary hover:text-foreground text-sm font-semibold transition-colors"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={isSaving || !name.trim()}
            className="px-5 py-2.5 rounded-xl bg-primary hover:bg-primary/90 text-primary-foreground text-sm font-bold flex items-center gap-2 shadow-sm transition-all disabled:opacity-50"
          >
            <Check className="w-4 h-4 stroke-[2.5]" />
            <span>{isSaving ? 'Saving...' : 'Save Profile'}</span>
          </button>
        </div>
      </form>
    </Modal>
  );
}
