import { createFileRoute } from '@tanstack/react-router';
import { z } from 'zod';
import { PatientWizard } from '@/features/patients/PatientWizard';

const searchSchema = z.object({
  firstName: z.string().optional(),
  lastName: z.string().optional(),
  dateOfBirth: z.string().optional(),
  mrn: z.string().optional(),
  cancerType: z.string().optional(),
  documentId: z.string().optional(),
});

export const Route = createFileRoute('/patients/new')({
  validateSearch: searchSchema,
  component: NewPatientPage,
});

function NewPatientPage() {
  const search = Route.useSearch();
  return (
    <div className="p-6 max-w-2xl mx-auto">
      <h1 className="text-3xl font-semibold tracking-tight mb-8">Add Patient</h1>
      <PatientWizard prefill={search} />
    </div>
  );
}
