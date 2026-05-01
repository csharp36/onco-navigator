import { useState } from 'react';
import { createFileRoute, Link, useNavigate } from '@tanstack/react-router';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { useDashboardStats } from '@/features/dashboard/api';
import { AlertCard } from '@/features/alerts/AlertCard';
import { ResolveAlertModal } from '@/features/alerts/ResolveAlertModal';
import type { AlertResponse } from '@/features/alerts/types';
import { DocumentDropZone } from '@/features/documents/DocumentDropZone';
import { DocumentProcessingModal } from '@/features/documents/DocumentProcessingModal';
import { PrefilledCareEventDialog } from '@/features/documents/PrefilledCareEventDialog';
import { useUploadDocument } from '@/features/documents/api';
import type { DocumentUploadResponse, DocumentPrefillData, DocumentType } from '@/features/documents/types';

export const Route = createFileRoute('/')({
  component: DashboardHome,
});

function DashboardHome() {
  const { data: stats, isLoading, isError } = useDashboardStats();
  const navigate = useNavigate();

  const [selectedAlert, setSelectedAlert] = useState<AlertResponse | null>(null);
  const [modalOpen, setModalOpen] = useState(false);

  // Document upload flow state
  const [uploadResult, setUploadResult] = useState<DocumentUploadResponse | null>(null);
  const [processingModalOpen, setProcessingModalOpen] = useState(false);
  const [prefillData, setPrefillData] = useState<DocumentPrefillData | null>(null);
  const [prefilledDialogOpen, setPrefilledDialogOpen] = useState(false);
  const uploadDocument = useUploadDocument();

  function handleUploadComplete(result: DocumentUploadResponse) {
    setUploadResult(result);
    setProcessingModalOpen(true);
  }

  function handlePatientSelected(patientId: string) {
    if (!uploadResult) return;
    const classification = uploadResult.classificationResult ?? {
      documentType: 'UNKNOWN',
      confidence: 'low',
      mrn: null,
      patientName: null,
      dateOfBirth: null,
      eventType: null,
      eventDate: null,
      extractedNotes: null,
    };
    setPrefillData({
      documentId: uploadResult.documentId,
      classification,
      patientId,
    });
    setProcessingModalOpen(false);
    setPrefilledDialogOpen(true);
  }

  function handleManualClassification(documentType: DocumentType) {
    if (!uploadResult) return;
    setUploadResult({
      ...uploadResult,
      classificationResult: {
        documentType,
        confidence: 'manual',
        mrn: null,
        patientName: null,
        dateOfBirth: null,
        eventType: null,
        eventDate: null,
        extractedNotes: null,
      },
    });
  }

  function handleResolve(alert: AlertResponse) {
    setSelectedAlert(alert);
    setModalOpen(true);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-semibold tracking-tight">Dashboard</h1>
      </div>

      {/* Stat cards */}
      {isLoading ? (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {[1, 2, 3].map((n) => (
            <Skeleton key={n} className="h-[100px] w-full rounded-xl" />
          ))}
        </div>
      ) : isError ? (
        <p className="text-sm text-muted-foreground">
          Unable to load alerts. Check your network connection and try refreshing.
        </p>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                Open Alerts
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p
                className={`text-3xl font-semibold ${(stats?.openAlertCount ?? 0) > 0 ? 'text-destructive' : ''}`}
              >
                {stats?.openAlertCount ?? 0}
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                Active Patients
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-3xl font-semibold">{stats?.activePatients ?? 0}</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                On-Track Patients
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-3xl font-semibold">{stats?.onTrackPatients ?? 0}</p>
            </CardContent>
          </Card>
        </div>
      )}

      {/* Document drop zone */}
      <DocumentDropZone
        variant="card"
        onUploadComplete={handleUploadComplete}
      />

      {/* Urgent alerts section */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-semibold">Urgent Alerts</h2>
          <Button variant="outline" asChild>
            <Link to="/alerts">View All Alerts</Link>
          </Button>
        </div>

        {isLoading && (
          <div className="space-y-3">
            {[1, 2, 3].map((n) => (
              <Skeleton key={n} className="h-[100px] w-full rounded-xl" />
            ))}
          </div>
        )}

        {!isLoading && !isError && (stats?.topUrgentAlerts ?? []).length === 0 && (
          <div className="space-y-1">
            <p className="text-sm font-medium">No urgent alerts.</p>
            <p className="text-sm text-muted-foreground">
              All monitored patients are on track.
            </p>
          </div>
        )}

        {!isLoading && !isError && (stats?.topUrgentAlerts ?? []).length > 0 && (
          <div className="space-y-3">
            {stats!.topUrgentAlerts.slice(0, 5).map((alert) => (
              <AlertCard key={alert.id} alert={alert} onResolve={handleResolve} />
            ))}
          </div>
        )}
      </div>

      <ResolveAlertModal
        alert={selectedAlert}
        open={modalOpen}
        onOpenChange={setModalOpen}
      />

      <DocumentProcessingModal
        open={processingModalOpen}
        onOpenChange={setProcessingModalOpen}
        uploadResult={uploadResult}
        isUploading={uploadDocument.isPending}
        onPatientSelected={handlePatientSelected}
        onManualClassification={handleManualClassification}
      />

      {prefillData && (
        <PrefilledCareEventDialog
          open={prefilledDialogOpen}
          onOpenChange={setPrefilledDialogOpen}
          patientId={prefillData.patientId}
          prefillData={prefillData}
        />
      )}
    </div>
  );
}
