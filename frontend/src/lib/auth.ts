import Keycloak from 'keycloak-js';

const keycloak = new Keycloak({
  url: 'http://localhost:9090',
  realm: 'onco-navigator',
  clientId: 'onco-navigator-app',
});

let initialized = false;

export async function initAuth(): Promise<boolean> {
  if (initialized) return keycloak.authenticated ?? false;

  try {
    const authenticated = await keycloak.init({
      onLoad: 'login-required',
      pkceMethod: 'S256',
      checkLoginIframe: false,
    });
    initialized = true;

    // Auto-refresh token before expiry
    setInterval(async () => {
      try {
        await keycloak.updateToken(30); // refresh if < 30s remaining
      } catch {
        keycloak.login();
      }
    }, 10000);

    return authenticated;
  } catch (error) {
    console.error('Keycloak init failed:', error);
    return false;
  }
}

export function getAccessToken(): string | undefined {
  return keycloak.token;
}

export function getUserRoles(): string[] {
  return keycloak.realmAccess?.roles?.filter(r => r.startsWith('ROLE_')) ?? [];
}

export function hasRole(role: string): boolean {
  return getUserRoles().includes(role);
}

export function getUserName(): string {
  return keycloak.tokenParsed?.preferred_username ?? 'Unknown';
}

export function getUserId(): string | undefined {
  return keycloak.subject;
}

export function logout(): void {
  keycloak.logout({ redirectUri: window.location.origin });
}

export function isAuthenticated(): boolean {
  return keycloak.authenticated ?? false;
}

export type UserRole = 'ROLE_NURSE_NAVIGATOR' | 'ROLE_CARE_COORDINATOR' | 'ROLE_ADMIN';

export const ROLE_LABELS: Record<UserRole, string> = {
  ROLE_NURSE_NAVIGATOR: 'Nurse Navigator',
  ROLE_CARE_COORDINATOR: 'Care Coordinator',
  ROLE_ADMIN: 'Administrator',
};
