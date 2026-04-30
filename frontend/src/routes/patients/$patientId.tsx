import { createFileRoute } from '@tanstack/react-router';

export const Route = createFileRoute('/patients/$patientId')({
  component: PatientDetailPage,
});

function PatientDetailPage() {
  const { patientId } = Route.useParams();
  return (
    <div className="p-6">
      <h1 className="text-3xl font-semibold tracking-tight">Patient Detail</h1>
      <p className="text-muted-foreground">Loading patient {patientId}...</p>
    </div>
  );
}
