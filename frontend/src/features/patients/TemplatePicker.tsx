import { useEffect } from 'react';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Label } from '@/components/ui/label';
import { usePathwayTemplates } from './api';
import type { PathwayTemplateResponse } from './types';

interface TemplatePickerProps {
  cancerType: string | null;
  pathwayMode: 'template' | 'empty';
  onPathwayModeChange: (value: 'template' | 'empty') => void;
  selectedTemplateId: string | null;
  onTemplateIdChange: (templateId: string | null) => void;
}

/**
 * Radio group for selecting the pathway setup mode in the patient enrollment wizard.
 *
 * Implements D-07: users choose between starting from a cancer-type template or
 * building the pathway from uploaded documents (empty pathway).
 *
 * When multiple templates exist for a cancer type (D-07), renders a variant
 * picker showing template names and descriptions (D-09). The root template
 * is pre-selected as the default (D-08).
 *
 * Renders only when a cancer type is selected.
 */
export function TemplatePicker({
  cancerType,
  pathwayMode,
  onPathwayModeChange,
  selectedTemplateId,
  onTemplateIdChange,
}: TemplatePickerProps) {
  const { data: templates, isLoading } = usePathwayTemplates(cancerType);

  const hasVariants = templates && templates.length > 1;

  // Auto-select root template when templates load and no selection exists (D-08)
  useEffect(() => {
    if (templates && templates.length > 0 && !selectedTemplateId) {
      const root = templates.find((t: PathwayTemplateResponse) => t.isRoot);
      if (root) onTemplateIdChange(root.id);
    }
  }, [templates, selectedTemplateId, onTemplateIdChange]);

  // Clear templateId when switching to empty pathway mode
  useEffect(() => {
    if (pathwayMode === 'empty') {
      onTemplateIdChange(null);
    }
  }, [pathwayMode, onTemplateIdChange]);

  if (!cancerType) return null;

  if (isLoading) {
    return (
      <div className="grid gap-2">
        <Label>Pathway Setup</Label>
        <p className="text-sm text-muted-foreground">Loading templates...</p>
      </div>
    );
  }

  // Determine display name for single-template case
  const singleTemplateName = templates && templates.length === 1
    ? templates[0].name
    : `${cancerType} Pathway`;

  return (
    <div className="grid gap-2">
      <Label>Pathway Setup</Label>
      <RadioGroup
        value={pathwayMode}
        onValueChange={(v) => onPathwayModeChange(v as 'template' | 'empty')}
      >
        <div className="flex items-center space-x-2">
          <RadioGroupItem value="template" id="pathway-template" />
          <Label htmlFor="pathway-template" className="font-normal">
            {hasVariants ? 'Start from template' : `Start from ${singleTemplateName}`}
          </Label>
        </div>
        <div className="flex items-center space-x-2">
          <RadioGroupItem value="empty" id="pathway-empty" />
          <Label htmlFor="pathway-empty" className="font-normal">
            Build from documents (empty pathway)
          </Label>
        </div>
      </RadioGroup>

      {/* Variant picker: shown when pathwayMode is 'template' and 2+ templates exist (D-07) */}
      {pathwayMode === 'template' && hasVariants && (
        <div className="grid gap-2 ml-6 mt-2">
          <Label className="text-sm text-muted-foreground">Template Variant</Label>
          <RadioGroup
            value={selectedTemplateId ?? ''}
            onValueChange={(id) => onTemplateIdChange(id)}
          >
            {templates.map((t: PathwayTemplateResponse) => (
              <div key={t.id} className="flex items-start space-x-2">
                <RadioGroupItem value={t.id} id={`template-${t.id}`} />
                <div className="grid gap-0.5">
                  <Label htmlFor={`template-${t.id}`} className="font-normal">
                    {t.name}
                  </Label>
                  {t.description && (
                    <p className="text-xs text-muted-foreground">{t.description}</p>
                  )}
                </div>
              </div>
            ))}
          </RadioGroup>
        </div>
      )}

      {pathwayMode === 'empty' && (
        <p className="text-sm text-muted-foreground">
          Steps will be added manually or extracted from uploaded documents.
        </p>
      )}
    </div>
  );
}
