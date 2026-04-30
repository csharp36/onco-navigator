import { createFileRoute } from '@tanstack/react-router';
import { PatientWizard } from '@/features/patients/PatientWizard';

export const Route = createFileRoute('/patients/new')({
  component: NewPatientPage,
});

function NewPatientPage() {
  return (
    <div className="p-6 max-w-2xl mx-auto">
      <h1 className="text-3xl font-semibold tracking-tight mb-8">Add Patient</h1>
      <PatientWizard />
    </div>
  );
}
