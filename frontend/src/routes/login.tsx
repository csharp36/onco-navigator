import { createFileRoute } from '@tanstack/react-router';
import { isAuthenticated } from '@/lib/auth';

export const Route = createFileRoute('/login')({
  component: LoginPage,
});

function LoginPage() {
  if (isAuthenticated()) {
    return (
      <div className="flex items-center justify-center h-screen">
        <p className="text-lg">You are already logged in. Redirecting...</p>
      </div>
    );
  }

  return (
    <div className="flex items-center justify-center h-screen">
      <div className="text-center">
        <h1 className="text-2xl font-bold">Onco-Navigator</h1>
        <p className="text-muted-foreground mt-2">Redirecting to login...</p>
      </div>
    </div>
  );
}
