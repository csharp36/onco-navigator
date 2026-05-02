import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigate } from '@tanstack/react-router';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { useCreatePatient } from '@/features/patients/api';

// ─── Zod v4 schemas (use { error: '...' } syntax) ────────────────────────────

const step1Schema = z.object({
  firstName: z.string().min(1, { error: 'First name is required.' }),
  lastName: z.string().min(1, { error: 'Last name is required.' }),
  dateOfBirth: z.string().min(1, { error: 'Date of birth is required.' }),
  mrn: z.string().min(1, { error: 'MRN is required.' }),
});

const step2Schema = z.object({
  cancerType: z.string().min(1, { error: 'Cancer type is required.' }),
  cancerStage: z.string().min(1, { error: 'Cancer stage is required.' }),
  diagnosisDate: z.string().min(1, { error: 'Diagnosis date is required.' }),
  assignedNavigator: z.string().optional(),
  treatingPhysician: z.string().optional(),
});

type Step1Values = z.infer<typeof step1Schema>;
type Step2Values = z.infer<typeof step2Schema>;

// ─── Step indicator ───────────────────────────────────────────────────────────

function StepIndicator({ currentStep }: { currentStep: 1 | 2 }) {
  return (
    <div className="flex items-center justify-center mb-8">
      {/* Step 1 */}
      <div
        className={`flex h-8 w-8 items-center justify-center rounded-full text-sm font-semibold ${
          currentStep === 1
            ? 'bg-primary text-primary-foreground'
            : 'bg-muted text-muted-foreground'
        }`}
      >
        1
      </div>
      {/* Connector line */}
      <div className="mx-2 h-px w-16 bg-border" />
      {/* Step 2 */}
      <div
        className={`flex h-8 w-8 items-center justify-center rounded-full text-sm font-semibold ${
          currentStep === 2
            ? 'bg-primary text-primary-foreground'
            : 'bg-muted text-muted-foreground'
        }`}
      >
        2
      </div>
    </div>
  );
}

// ─── Prefill props (from document classification) ────────────────────────────

interface PatientWizardProps {
  prefill?: {
    firstName?: string;
    lastName?: string;
    dateOfBirth?: string;
    mrn?: string;
    cancerType?: string;
    documentId?: string;
  };
}

// ─── Main wizard component ────────────────────────────────────────────────────

export function PatientWizard({ prefill }: PatientWizardProps) {
  const [step, setStep] = useState<1 | 2>(1);
  const [step1Data, setStep1Data] = useState<Step1Values | null>(null);
  const [mutationError, setMutationError] = useState<string | null>(null);

  const navigate = useNavigate();
  const createPatient = useCreatePatient();

  // Step 1 form — pre-fill from document classification if available
  const form1 = useForm<Step1Values>({
    resolver: zodResolver(step1Schema),
    defaultValues: {
      firstName: prefill?.firstName ?? '',
      lastName: prefill?.lastName ?? '',
      dateOfBirth: prefill?.dateOfBirth ?? '',
      mrn: prefill?.mrn ?? '',
    },
  });

  // Step 2 form — pre-fill cancer type from document classification
  const form2 = useForm<Step2Values>({
    resolver: zodResolver(step2Schema),
    defaultValues: {
      cancerType: prefill?.cancerType ?? '',
      cancerStage: '',
      diagnosisDate: '',
      assignedNavigator: '',
      treatingPhysician: '',
    },
  });

  function handleStep1Next(values: Step1Values) {
    setStep1Data(values);
    setStep(2);
  }

  function handleBack() {
    setStep(1);
    // Re-populate step 1 form with previously entered data
    if (step1Data) {
      form1.reset(step1Data);
    }
  }

  async function handleStep2Submit(values: Step2Values) {
    if (!step1Data) return;
    setMutationError(null);

    const payload = {
      firstName: step1Data.firstName,
      lastName: step1Data.lastName,
      dateOfBirth: step1Data.dateOfBirth,
      mrn: step1Data.mrn,
      cancerType: values.cancerType as 'BREAST' | 'LUNG' | 'COLORECTAL',
      cancerStage: values.cancerStage,
      diagnosisDate: values.diagnosisDate,
      // assignedNavigatorId is UUID on backend — V1 has no user directory, omit it
      treatingPhysician: values.treatingPhysician || undefined,
    };

    createPatient.mutate(payload, {
      onSuccess: (data) => {
        navigate({
          to: '/patients/$patientId',
          params: { patientId: data.id },
          search: prefill?.documentId ? { documentId: prefill.documentId } : {},
        });
      },
      onError: () => {
        setMutationError(
          'An error occurred while saving. Your changes were not saved. Please try again.'
        );
      },
    });
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-xl font-semibold">
          {step === 1 ? 'Step 1: Demographics' : 'Step 2: Clinical Details'}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <StepIndicator currentStep={step} />

        {/* ── Step 1 ── */}
        {step === 1 && (
          <form onSubmit={form1.handleSubmit(handleStep1Next)} noValidate>
            <div className="space-y-4">
              {/* First Name */}
              <div className="grid gap-2">
                <Label htmlFor="firstName">First Name</Label>
                <Input
                  id="firstName"
                  {...form1.register('firstName')}
                  aria-invalid={!!form1.formState.errors.firstName}
                />
                {form1.formState.errors.firstName && (
                  <p className="text-destructive text-xs">
                    {form1.formState.errors.firstName.message}
                  </p>
                )}
              </div>

              {/* Last Name */}
              <div className="grid gap-2">
                <Label htmlFor="lastName">Last Name</Label>
                <Input
                  id="lastName"
                  {...form1.register('lastName')}
                  aria-invalid={!!form1.formState.errors.lastName}
                />
                {form1.formState.errors.lastName && (
                  <p className="text-destructive text-xs">
                    {form1.formState.errors.lastName.message}
                  </p>
                )}
              </div>

              {/* Date of Birth */}
              <div className="grid gap-2">
                <Label htmlFor="dateOfBirth">Date of Birth</Label>
                <Input
                  id="dateOfBirth"
                  type="date"
                  {...form1.register('dateOfBirth')}
                  aria-invalid={!!form1.formState.errors.dateOfBirth}
                />
                {form1.formState.errors.dateOfBirth && (
                  <p className="text-destructive text-xs">
                    {form1.formState.errors.dateOfBirth.message}
                  </p>
                )}
              </div>

              {/* MRN */}
              <div className="grid gap-2">
                <Label htmlFor="mrn">MRN</Label>
                <Input
                  id="mrn"
                  {...form1.register('mrn')}
                  aria-invalid={!!form1.formState.errors.mrn}
                />
                {form1.formState.errors.mrn && (
                  <p className="text-destructive text-xs">
                    {form1.formState.errors.mrn.message}
                  </p>
                )}
              </div>

              <div className="flex justify-end pt-2">
                <Button type="submit">Next: Clinical Details</Button>
              </div>
            </div>
          </form>
        )}

        {/* ── Step 2 ── */}
        {step === 2 && (
          <form onSubmit={form2.handleSubmit(handleStep2Submit)} noValidate>
            <div className="space-y-4">
              {/* Cancer Type */}
              <div className="grid gap-2">
                <Label htmlFor="cancerType">Cancer Type</Label>
                <Select
                  onValueChange={(value) =>
                    form2.setValue('cancerType', value, { shouldValidate: true })
                  }
                  defaultValue={form2.getValues('cancerType')}
                >
                  <SelectTrigger
                    id="cancerType"
                    className="w-full"
                    aria-invalid={!!form2.formState.errors.cancerType}
                  >
                    <SelectValue placeholder="Select cancer type" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="BREAST">Breast</SelectItem>
                    <SelectItem value="LUNG">Lung</SelectItem>
                    <SelectItem value="COLORECTAL">Colorectal</SelectItem>
                  </SelectContent>
                </Select>
                {form2.formState.errors.cancerType && (
                  <p className="text-destructive text-xs">
                    {form2.formState.errors.cancerType.message}
                  </p>
                )}
              </div>

              {/* Cancer Stage */}
              <div className="grid gap-2">
                <Label htmlFor="cancerStage">Cancer Stage</Label>
                <Select
                  onValueChange={(value) =>
                    form2.setValue('cancerStage', value, { shouldValidate: true })
                  }
                  defaultValue={form2.getValues('cancerStage')}
                >
                  <SelectTrigger
                    id="cancerStage"
                    className="w-full"
                    aria-invalid={!!form2.formState.errors.cancerStage}
                  >
                    <SelectValue placeholder="Select stage" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="I">I</SelectItem>
                    <SelectItem value="IA">IA</SelectItem>
                    <SelectItem value="IB">IB</SelectItem>
                    <SelectItem value="II">II</SelectItem>
                    <SelectItem value="IIA">IIA</SelectItem>
                    <SelectItem value="IIB">IIB</SelectItem>
                    <SelectItem value="III">III</SelectItem>
                    <SelectItem value="IIIA">IIIA</SelectItem>
                    <SelectItem value="IIIB">IIIB</SelectItem>
                    <SelectItem value="IIIC">IIIC</SelectItem>
                    <SelectItem value="IV">IV</SelectItem>
                    <SelectItem value="IVA">IVA</SelectItem>
                    <SelectItem value="IVB">IVB</SelectItem>
                  </SelectContent>
                </Select>
                {form2.formState.errors.cancerStage && (
                  <p className="text-destructive text-xs">
                    {form2.formState.errors.cancerStage.message}
                  </p>
                )}
              </div>

              {/* Diagnosis Date */}
              <div className="grid gap-2">
                <Label htmlFor="diagnosisDate">Diagnosis Date</Label>
                <Input
                  id="diagnosisDate"
                  type="date"
                  {...form2.register('diagnosisDate')}
                  aria-invalid={!!form2.formState.errors.diagnosisDate}
                />
                {form2.formState.errors.diagnosisDate && (
                  <p className="text-destructive text-xs">
                    {form2.formState.errors.diagnosisDate.message}
                  </p>
                )}
              </div>

              {/* Assigned Navigator (freetext, optional — V1 has no user directory) */}
              <div className="grid gap-2">
                <Label htmlFor="assignedNavigator">
                  Assigned Navigator{' '}
                  <span className="text-muted-foreground font-normal">(optional)</span>
                </Label>
                <Input
                  id="assignedNavigator"
                  placeholder="Navigator name"
                  {...form2.register('assignedNavigator')}
                />
              </div>

              {/* Treating Physician (optional) */}
              <div className="grid gap-2">
                <Label htmlFor="treatingPhysician">
                  Treating Physician{' '}
                  <span className="text-muted-foreground font-normal">(optional)</span>
                </Label>
                <Input
                  id="treatingPhysician"
                  placeholder="Physician name"
                  {...form2.register('treatingPhysician')}
                />
              </div>

              {mutationError && (
                <p className="text-destructive text-sm">{mutationError}</p>
              )}

              <div className="flex justify-between pt-2">
                <Button type="button" variant="outline" onClick={handleBack}>
                  Back
                </Button>
                <Button type="submit" disabled={createPatient.isPending}>
                  {createPatient.isPending ? 'Enrolling...' : 'Enroll Patient'}
                </Button>
              </div>
            </div>
          </form>
        )}
      </CardContent>
    </Card>
  );
}
