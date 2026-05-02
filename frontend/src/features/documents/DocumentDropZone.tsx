import { useState } from 'react';
import { useDropzone, type FileRejection } from 'react-dropzone';
import { Upload } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useUploadDocument } from './api';
import type { DocumentUploadResponse } from './types';

interface DocumentDropZoneProps {
  patientId?: string;
  onUploadComplete: (result: DocumentUploadResponse) => void;
  onUploadStart?: () => void;
  variant?: 'card' | 'button';
}

export function DocumentDropZone({
  patientId,
  onUploadComplete,
  onUploadStart,
  variant = 'card',
}: DocumentDropZoneProps) {
  const uploadDocument = useUploadDocument();
  const [error, setError] = useState<string | null>(null);

  function handleDrop(acceptedFiles: File[]) {
    setError(null);
    if (acceptedFiles.length === 0) return;

    const formData = new FormData();
    formData.append('file', acceptedFiles[0]);
    if (patientId) formData.append('patientId', patientId);

    onUploadStart?.();
    uploadDocument.mutate(formData, {
      onSuccess: onUploadComplete,
      onError: () => {
        setError('Upload failed. Please try again.');
      },
    });
  }

  function handleDropRejected(rejections: FileRejection[]) {
    if (rejections.length === 0) return;

    const rejection = rejections[0];
    const errorCode = rejection.errors[0]?.code;

    if (errorCode === 'too-many-files') {
      setError('Please upload one document at a time.');
    } else if (errorCode === 'file-invalid-type') {
      setError('Unsupported file type. Please upload a PDF, JPEG, or PNG.');
    } else if (errorCode === 'file-too-large') {
      setError('File exceeds 20 MB limit.');
    } else {
      setError('File could not be accepted. Please try again.');
    }
  }

  const { getRootProps, getInputProps, isDragActive, isDragReject } = useDropzone({
    accept: {
      'application/pdf': ['.pdf'],
      'image/jpeg': ['.jpg', '.jpeg'],
      'image/png': ['.png'],
    },
    maxFiles: 1,
    maxSize: 20 * 1024 * 1024,
    onDrop: handleDrop,
    onDropRejected: handleDropRejected,
    disabled: uploadDocument.isPending,
  });

  if (variant === 'button') {
    return (
      <div>
        <Button
          variant="outline"
          size="sm"
          disabled={uploadDocument.isPending}
          {...getRootProps()}
          role="button"
          tabIndex={0}
          aria-label="Upload clinical document. Drop a file here or press Enter to browse."
        >
          <input {...getInputProps()} />
          <Upload />
          {uploadDocument.isPending ? 'Uploading...' : 'Upload Document'}
        </Button>
        {error && (
          <p className="text-destructive text-sm mt-1">{error}</p>
        )}
      </div>
    );
  }

  return (
    <div>
      <div
        {...getRootProps()}
        role="button"
        tabIndex={0}
        aria-label="Upload clinical document. Drop a file here or press Enter to browse."
        className={`border-2 border-dashed rounded-lg p-8 text-center cursor-pointer transition-colors
          ${isDragActive && !isDragReject ? 'border-ring bg-muted/50' : 'border-border hover:border-muted-foreground/50'}
          ${isDragReject ? 'border-destructive bg-destructive/5' : ''}
          ${uploadDocument.isPending ? 'opacity-50 pointer-events-none' : ''}`}
      >
        <input {...getInputProps()} />
        <Upload className="mx-auto size-8 text-muted-foreground mb-2" />
        <p className="text-sm text-muted-foreground">
          {isDragActive
            ? 'Release to upload'
            : 'Drop a clinical document here, or click to browse'}
        </p>
        <p className="text-xs text-muted-foreground mt-1">
          Accepts PDF, JPEG, PNG -- one file at a time
        </p>
      </div>
      {error && (
        <p className="text-destructive text-sm mt-2">{error}</p>
      )}
    </div>
  );
}
