import { useEffect, useState } from 'react';
import { Download, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from '@/components/ui/sheet';
import { getAccessToken } from '@/lib/auth';

// ─── Props ───────────────────────────────────────────────────────────────────

interface DocumentPreviewPanelProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  documentId: string;
  filename: string;
}

// ─── Component ───────────────────────────────────────────────────────────────

export function DocumentPreviewPanel({
  open,
  onOpenChange,
  documentId,
  filename,
}: DocumentPreviewPanelProps) {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const contentUrl = `/api/documents/${documentId}/content`;

  // Fetch the PDF as a blob with auth token so iframe can display it
  useEffect(() => {
    if (!open || !documentId) return;

    let revoked = false;
    setLoading(true);
    setError(false);
    setBlobUrl(null);

    const token = getAccessToken();
    fetch(contentUrl, {
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        Accept: 'application/pdf, image/*, application/octet-stream',
      },
    })
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.blob();
      })
      .then((blob) => {
        if (!revoked) {
          setBlobUrl(URL.createObjectURL(blob));
        }
      })
      .catch(() => {
        if (!revoked) setError(true);
      })
      .finally(() => {
        if (!revoked) setLoading(false);
      });

    return () => {
      revoked = true;
    };
  }, [open, documentId, contentUrl]);

  // Clean up blob URL on unmount
  useEffect(() => {
    return () => {
      if (blobUrl) URL.revokeObjectURL(blobUrl);
    };
  }, [blobUrl]);

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-[600px] sm:max-w-[600px]">
        <SheetHeader>
          <SheetTitle className="truncate">{filename}</SheetTitle>
          <SheetDescription>Document preview</SheetDescription>
        </SheetHeader>

        <div className="flex items-center gap-2 px-4">
          <Button variant="outline" size="sm" asChild>
            <a href={blobUrl ?? contentUrl} download={filename}>
              <Download className="mr-1 size-4" />
              Download Document
            </a>
          </Button>
        </div>

        <div className="flex-1 px-4 pb-4">
          {loading && (
            <div className="flex items-center justify-center h-full min-h-[600px] rounded-md border bg-muted/20">
              <Loader2 className="size-8 animate-spin text-muted-foreground" />
            </div>
          )}
          {error && (
            <div className="flex items-center justify-center h-full min-h-[600px] rounded-md border bg-muted/20 p-6 text-center">
              <div className="space-y-2">
                <p className="text-sm text-muted-foreground">
                  Unable to load document preview. Download the document to view it.
                </p>
                <Button variant="outline" size="sm" asChild>
                  <a href={contentUrl} download={filename}>
                    <Download className="mr-1 size-4" />
                    Download
                  </a>
                </Button>
              </div>
            </div>
          )}
          {!loading && !error && blobUrl && (
            <iframe
              src={blobUrl}
              className="w-full h-full min-h-[600px] rounded-md border"
              title="Clinical document preview"
            />
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}
