import { createFileRoute } from '@tanstack/react-router';

export const Route = createFileRoute('/patients/new')({
  component: NewPatientPage,
});

function NewPatientPage() {
  return (
    <div className="p-6">
      <h1 className="text-3xl font-semibold tracking-tight">Add Patient</h1>
      <p className="text-muted-foreground">Loading wizard...</p>
    </div>
  );
}
