import { createFileRoute } from '@tanstack/react-router';

export const Route = createFileRoute('/alerts/')({
  component: AlertQueuePage,
});

function AlertQueuePage() {
  return (
    <div className="p-6">
      <h1 className="text-3xl font-semibold tracking-tight">Alerts</h1>
      <p className="text-muted-foreground">Loading alerts...</p>
    </div>
  );
}
