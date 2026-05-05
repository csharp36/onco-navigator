import { Link } from '@tanstack/react-router';
import { Card, CardContent, CardFooter, CardHeader } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import type { AlertResponse } from './types';

interface AlertCardProps {
  alert: AlertResponse;
  onResolve: (alert: AlertResponse) => void;
}

function getSeverityBorderColor(severityLabel: AlertResponse['severityLabel']): string {
  switch (severityLabel) {
    case 'OVERDUE':
      return 'var(--severity-overdue)';
    case 'CANCELLED':
      return 'var(--severity-overdue)';
    case 'RESULTS PENDING':
      return 'var(--severity-missing)';
    case 'DEADLINE':
      return 'var(--severity-missing)';
    case 'MISSING':
      return 'var(--severity-missing)';
    case 'UNCONFIRMED':
      return 'var(--severity-out-of-order)';
    default:
      return 'var(--severity-out-of-order)';
  }
}

function getSeverityBadgeVariant(
  severityLabel: AlertResponse['severityLabel'],
): 'destructive' | 'default' | 'secondary' | 'outline' {
  switch (severityLabel) {
    case 'OVERDUE':
    case 'CANCELLED':
      return 'destructive';
    case 'MISSING':
    case 'RESULTS PENDING':
    case 'DEADLINE':
      return 'default';
    default:
      return 'secondary';
  }
}

export function AlertCard({ alert, onResolve }: AlertCardProps) {
  return (
    <Card
      className="border-l-4"
      style={{ borderLeftColor: getSeverityBorderColor(alert.severityLabel) }}
    >
      <CardHeader className="pb-2">
        <div className="flex flex-wrap items-start gap-2">
          <Badge variant={getSeverityBadgeVariant(alert.severityLabel)}>
            {alert.severityLabel}
          </Badge>
          <div className="flex flex-col gap-0.5">
            <Link
              to="/patients/$patientId"
              params={{ patientId: alert.patientId }}
              className="font-medium hover:underline"
            >
              {alert.patientName}
            </Link>
            <span className="text-sm text-muted-foreground">MRN: {alert.patientMrn}</span>
          </div>
        </div>
      </CardHeader>
      <CardContent className="py-2">
        <p className="text-sm font-semibold">{alert.pathwayStepName}</p>
        <p className="mt-1 text-sm">{alert.deviationDescription}</p>
        <p className="mt-1 text-sm text-muted-foreground italic">{alert.suggestedAction}</p>
      </CardContent>
      <CardFooter className="flex items-center justify-between gap-2 pt-2">
        <span className="text-sm text-muted-foreground">{alert.timeElapsed}</span>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" asChild>
            <Link to="/patients/$patientId" params={{ patientId: alert.patientId }}>
              View
            </Link>
          </Button>
          <Button size="sm" onClick={() => onResolve(alert)}>
            Resolve
          </Button>
        </div>
      </CardFooter>
    </Card>
  );
}
