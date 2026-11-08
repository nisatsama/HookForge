import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { SignIn } from './Pages';
import { AuthProvider } from '../context/AuthContext';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '../test/setup';

describe('API-key authentication experience', () => {
  it('asks for a provisioned API key without an email/password flow', () => {
    render(<QueryClientProvider client={new QueryClient()}><AuthProvider><SignIn /></AuthProvider></QueryClientProvider>);
    expect(screen.getByLabelText(/api key/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /continue securely/i })).toBeDisabled();
    expect(screen.queryByLabelText(/email/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/password/i)).not.toBeInTheDocument();
  });
});
