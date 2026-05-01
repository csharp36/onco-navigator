import { useState } from 'react';
import { Download } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from '@/components/ui/sheet';
import { getDocumentContentUrl } from './api';

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
  const [iframeError, setIframeError] = useState(false);
  const contentUrl = getDocumentContentUrl(documentId);

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-[600px] sm:max-w-[600px]">
        <SheetHeader>
          <SheetTitle className="truncate">{filename}</SheetTitle>
          <SheetDescription>Document preview</SheetDescription>
        </SheetHeader>

        <div className="flex items-center gap-2 px-4">
          <Button variant="outline" size="sm" asChild>
            <a href={contentUrl} download={filename}>
              <Download className="mr-1 size-4" />
              Download Document
            </a>
          </Button>
        </div>

        <div className="flex-1 px-4 pb-4">
          {iframeError ? (
            <div className="flex items-center justify-center h-full min-h-[600px] rounded-md border bg-muted/20 p-6 text-center">
              <div className="space-y-2">
                <p className="text-sm text-muted-foreground">
                  Your browser cannot display this PDF inline. Download the document to view it.
                </p>
                <Button variant="outline" size="sm" asChild>
                  <a href={contentUrl} download={filename}>
                    <Download className="mr-1 size-4" />
                    Download
                  </a>
                </Button>
              </div>
            </div>
          ) : (
            <iframe
              src={contentUrl}
              className="w-full h-full min-h-[600px] rounded-md border"
              title="Clinical document preview"
              onError={() => setIframeError(true)}
            />
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}
