'use client';

import React, { useState } from 'react';
import { useFinance } from '@/context/finance-context';
import { CategoryIcon, AVAILABLE_ICONS, PALETTE_COLORS } from '@/components/category-icon';
import { Modal } from '@/components/ui/modal';
import { Category, TransactionType } from '@/types/database';
import { Plus, Edit2, Trash2 } from 'lucide-react';

export default function CategoriesPage() {
  const { categories, addCategory, updateCategory, deleteCategory } = useFinance();

  const [activeTab, setActiveTab] = useState<TransactionType>('expense');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState<Category | null>(null);

  const [name, setName] = useState('');
  const [type, setType] = useState<TransactionType>('expense');
  const [icon, setIcon] = useState('Tag');
  const [color, setColor] = useState('#6366F1');

  const filteredCategories = categories.filter((c) => c.type === activeTab);

  const openCreateModal = () => {
    setEditingCategory(null);
    setName('');
    setType(activeTab);
    setIcon('Tag');
    setColor('#6366F1');
    setIsModalOpen(true);
  };

  const openEditModal = (cat: Category) => {
    setEditingCategory(cat);
    setName(cat.name);
    setType(cat.type);
    setIcon(cat.icon);
    setColor(cat.color);
    setIsModalOpen(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;

    if (editingCategory) {
      await updateCategory(editingCategory.id, {
        name: name.trim(),
        type,
        icon,
        color,
      });
    } else {
      await addCategory({
        name: name.trim(),
        type,
        icon,
        color,
      });
    }
    setIsModalOpen(false);
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-300 max-w-6xl mx-auto pb-16">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-foreground">
            Categories
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Custom category tags, icons, and color themes
          </p>
        </div>

        <button
          onClick={openCreateModal}
          className="px-4 py-2.5 rounded-2xl bg-primary hover:bg-primary/90 text-primary-foreground text-xs font-semibold flex items-center gap-2 shadow-sm transition-opacity self-start sm:self-auto"
        >
          <Plus className="w-4 h-4 stroke-[2.5]" />
          <span>New Category</span>
        </button>
      </div>

      {/* Segmented Pill Tabs */}
      <div className="flex gap-2 p-1 bg-secondary rounded-2xl w-fit">
        <button
          onClick={() => setActiveTab('expense')}
          className={`py-2 px-6 rounded-xl text-xs font-bold transition-all ${
            activeTab === 'expense'
              ? 'bg-rose-500 text-white shadow-sm'
              : 'text-muted-foreground hover:text-foreground'
          }`}
        >
          Expenses ({categories.filter((c) => c.type === 'expense').length})
        </button>
        <button
          onClick={() => setActiveTab('income')}
          className={`py-2 px-6 rounded-xl text-xs font-bold transition-all ${
            activeTab === 'income'
              ? 'bg-emerald-500 text-white shadow-sm'
              : 'text-muted-foreground hover:text-foreground'
          }`}
        >
          Incomes ({categories.filter((c) => c.type === 'income').length})
        </button>
      </div>

      {/* Category Cards Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
        {filteredCategories.map((cat) => (
          <div
            key={cat.id}
            className="p-5 rounded-3xl fintech-card flex items-center justify-between group"
          >
            <div className="flex items-center gap-3.5">
              <CategoryIcon icon={cat.icon} color={cat.color} size={42} />
              <div>
                <h3 className="text-sm font-bold text-foreground">{cat.name}</h3>
                <span className="text-[11px] text-muted-foreground">
                  {cat.is_default ? 'System Default' : 'Custom Tag'}
                </span>
              </div>
            </div>

            <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
              <button
                onClick={() => openEditModal(cat)}
                className="p-2 rounded-xl text-muted-foreground hover:bg-secondary hover:text-foreground transition-colors"
                title="Edit Category"
              >
                <Edit2 className="w-3.5 h-3.5" />
              </button>
              {!cat.is_default && (
                <button
                  onClick={() => deleteCategory(cat.id)}
                  className="p-2 rounded-xl text-muted-foreground hover:bg-rose-500/10 hover:text-rose-500 transition-colors"
                  title="Delete Category"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
          </div>
        ))}
      </div>

      {/* Create / Edit Modal */}
      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title={editingCategory ? 'Edit Category' : 'New Category'}
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-muted-foreground uppercase tracking-wider">
              Category Name
            </label>
            <input
              type="text"
              placeholder="e.g. Freelancing, Coffee, Fitness"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full px-3.5 py-2.5 text-sm bg-secondary/60 border border-border rounded-xl focus:ring-1 focus:ring-primary focus:outline-none text-foreground"
            />
          </div>

          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-muted-foreground uppercase tracking-wider">
              Type
            </label>
            <div className="grid grid-cols-2 gap-2 p-1 bg-secondary rounded-xl">
              <button
                type="button"
                onClick={() => setType('expense')}
                className={`py-2 text-xs font-bold rounded-lg transition-all ${
                  type === 'expense'
                    ? 'bg-rose-500 text-white'
                    : 'text-muted-foreground'
                }`}
              >
                Expense
              </button>
              <button
                type="button"
                onClick={() => setType('income')}
                className={`py-2 text-xs font-bold rounded-lg transition-all ${
                  type === 'income'
                    ? 'bg-emerald-500 text-white'
                    : 'text-muted-foreground'
                }`}
              >
                Income
              </button>
            </div>
          </div>

          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-muted-foreground uppercase tracking-wider">
              Choose Icon
            </label>
            <div className="flex flex-wrap gap-2 max-h-32 overflow-y-auto p-2 bg-secondary/40 border border-border rounded-xl">
              {AVAILABLE_ICONS.map((i) => (
                <button
                  key={i}
                  type="button"
                  onClick={() => setIcon(i)}
                  className={`p-2.5 rounded-xl border transition-all ${
                    icon === i
                      ? 'border-primary bg-primary/20 text-primary'
                      : 'border-transparent text-muted-foreground hover:bg-secondary'
                  }`}
                >
                  <CategoryIcon icon={i} color={icon === i ? color : '#94A3B8'} size={20} />
                </button>
              ))}
            </div>
          </div>

          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-muted-foreground uppercase tracking-wider">
              Choose Color
            </label>
            <div className="flex flex-wrap gap-2.5 p-2 bg-secondary/40 border border-border rounded-xl">
              {PALETTE_COLORS.map((c) => (
                <button
                  key={c}
                  type="button"
                  onClick={() => setColor(c)}
                  className={`w-7 h-7 rounded-full transition-transform ${
                    color === c ? 'scale-125 ring-2 ring-primary ring-offset-2 ring-offset-card' : 'hover:scale-110'
                  }`}
                  style={{ backgroundColor: c }}
                />
              ))}
            </div>
          </div>

          <div className="flex items-center gap-3 pt-3">
            <button
              type="button"
              onClick={() => setIsModalOpen(false)}
              className="flex-1 py-2.5 px-4 rounded-xl bg-secondary text-foreground text-xs font-semibold"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="flex-1 py-2.5 px-4 rounded-xl bg-primary text-primary-foreground text-xs font-semibold shadow-sm"
            >
              {editingCategory ? 'Update' : 'Create'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
