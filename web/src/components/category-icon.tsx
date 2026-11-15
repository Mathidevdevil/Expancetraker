import React from 'react';
import * as Icons from 'lucide-react';

interface CategoryIconProps {
  name?: string;
  icon?: string;
  color?: string;
  className?: string;
  size?: number;
}

export function CategoryIcon({ name, icon, color, className = 'w-5 h-5', size }: CategoryIconProps) {
  const iconName = icon || name || 'Tag';

  // Normalize name
  const formattedName = iconName
    .replace(/[^a-zA-Z0-9]/g, '')
    .trim();

  // Find component from lucide-react
  // @ts-ignore
  const IconComponent = Icons[iconName] || Icons[formattedName] || Icons.Tag;

  return (
    <div
      className={`inline-flex items-center justify-center rounded-xl p-2 transition-transform ${className}`}
      style={{ backgroundColor: color ? `${color}20` : '#6366F120', color: color || '#6366F1' }}
    >
      <IconComponent size={size || 18} />
    </div>
  );
}

export const AVAILABLE_ICONS = [
  'Utensils',
  'Car',
  'ShoppingBag',
  'Film',
  'Zap',
  'HeartPulse',
  'Plane',
  'ShoppingCart',
  'GraduationCap',
  'Briefcase',
  'TrendingUp',
  'Laptop',
  'Gift',
  'Coffee',
  'Home',
  'Fuel',
  'Phone',
  'Book',
  'Smile',
];

export const PALETTE_COLORS = [
  '#6366F1', // Indigo
  '#10B981', // Emerald
  '#F43F5E', // Rose
  '#F59E0B', // Amber
  '#06B6D4', // Cyan
  '#8B5CF6', // Purple
  '#EC4899', // Pink
  '#3B82F6', // Blue
];
