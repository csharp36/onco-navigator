import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Label } from '@/components/ui/label';

interface TemplatePickerProps {
  cancerType: string | null;
  value: 'template' | 'empty';
  onChange: (value: 'template' | 'empty') => void;
}

// Template display names per cancer type
const TEMPLATE_NAMES: Record<string, string> = {
  BREAST: 'Breast Cancer Pathway',
  LUNG: 'Lung Cancer Pathway',
  COLORECTAL: 'Colorectal Cancer Pathway',
};

/**
 * Radio group for selecting the pathway setup mode in the patient enrollment wizard.
 *
 * Implements D-07: users choose between starting from a cancer-type template or
 * building the pathway from uploaded documents (empty pathway).
 *
 * Renders only when a cancer type is selected. Resets to 'template' when the
 * cancer type changes (caller is responsible for resetting state on type change).
 */
export function TemplatePicker({ cancerType, value, onChange }: TemplatePickerProps) {
  if (!cancerType) return null;

  const templateName = TEMPLATE_NAMES[cancerType] ?? `${cancerType} Pathway`;

  return (
    <div className="grid gap-2">
      <Label>Pathway Setup</Label>
      <RadioGroup
        value={value}
        onValueChange={(v) => onChange(v as 'template' | 'empty')}
      >
        <div className="flex items-center space-x-2">
          <RadioGroupItem value="template" id="pathway-template" />
          <Label htmlFor="pathway-template" className="font-normal">
            Start from {templateName}
          </Label>
        </div>
        <div className="flex items-center space-x-2">
          <RadioGroupItem value="empty" id="pathway-empty" />
          <Label htmlFor="pathway-empty" className="font-normal">
            Build from documents (empty pathway)
          </Label>
        </div>
      </RadioGroup>
      {value === 'empty' && (
        <p className="text-sm text-muted-foreground">
          Steps will be added manually or extracted from uploaded documents.
        </p>
      )}
    </div>
  );
}
