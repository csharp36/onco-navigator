import { createFileRoute } from '@tanstack/react-router';

export const Route = createFileRoute('/patients/')({
  component: PatientListPage,
});

function PatientListPage() {
  return (
    <div className="p-6">
      <h1 className="text-3xl font-semibold tracking-tight">Patients</h1>
      <p className="text-muted-foreground">Loading patient list...</p>
    </div>
  );
}
